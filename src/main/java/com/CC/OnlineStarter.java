package com.CC;

import com.CC.Constraints.Rules.Rule;
import com.CC.Constraints.Rules.RuleHandler;
import com.CC.Constraints.Runtime.Link;
import com.CC.Contexts.Context;
import com.CC.Contexts.ContextChange;
import com.CC.Contexts.ContextHandler;
import com.CC.Contexts.ContextPool;
import com.CC.Middleware.Checkers.*;
import com.CC.Middleware.Schedulers.*;
import com.CC.Patterns.PatternHandler;
import com.CC.Util.Loggable;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class OnlineStarter implements Loggable {

    public static final int dataPacketLen = 1024;

    static class CCEServer implements Callable<Void>{
        private final String ruleFile;
        private final String bfuncFile;
        private final String patternFile;
        private final String incOutFile;

        private final RuleHandler ruleHandler;
        private final PatternHandler patternHandler;
        private final ContextHandler contextHandler;
        private final ContextPool contextPool;
        private Scheduler scheduler;
        private Checker checker;
        private final String runType;

        private long oldTime_gen = 0L;
        private long totalTime_gen = 0L;
        private long totalTime_det = 0L;

        private final Queue<ContextChange> changeQueue = new LinkedList<>();
        private boolean cleaned = false;

        public CCEServer(String approach, String ruleFile, String bfuncFile, String patternFile, boolean isMG, String incOutFile, String runType) {
            this.ruleFile = ruleFile;
            this.bfuncFile = bfuncFile;
            this.patternFile = patternFile;
            this.incOutFile = incOutFile;
            this.runType = runType;

            this.ruleHandler = new RuleHandler();
            // use switch to create specific patternHandler and contextHandler
            // ...
            this.contextHandler = null;
            this.patternHandler = null;
            this.contextPool = new ContextPool();

            try {
                buildRulesAndPatterns();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Object bfuncInstance = null;
            try {
                bfuncInstance = loadBfuncFile();
                logger.info("Load bfunctions successfully.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            String technique = null;
            String schedule = null;
            if(approach.contains("+")){
                technique = approach.substring(0, approach.indexOf("+"));
                schedule = approach.substring(approach.indexOf("+") + 1);
            }
            else{
                if(approach.equalsIgnoreCase("INFUSE_base")){
                    technique = "INFUSE_base";
                    schedule = "IMD";
                }
                else if(approach.equalsIgnoreCase("INFUSE")){
                    technique = "INFUSE_C";
                    schedule = "INFUSE_S";
                }
            }
            logger.debug("Checking technique is " + technique + ", scheduling strategy is " + schedule + ", with MG " + (isMG ? "on" : "off"));

            assert technique != null;

            switch (technique) {
                case "ECC":
                    this.checker = new ECC(this.ruleHandler, this.contextPool, bfuncInstance, isMG);
                    break;
                case "ConC":
                    this.checker = new ConC(this.ruleHandler, this.contextPool, bfuncInstance, isMG);
                    break;
                case "PCC":
                    this.checker = new PCC(this.ruleHandler, this.contextPool, bfuncInstance, isMG);
                    break;
                case "INFUSE_base":
                    this.checker = new BASE(this.ruleHandler, this.contextPool, bfuncInstance, isMG);
                    break;
                case "INFUSE_C":
                    this.checker = new INFUSE_C(this.ruleHandler, this.contextPool, bfuncInstance, isMG);
                    break;
            }

            switch (schedule){
                case "IMD":
                    this.scheduler = new IMD(ruleHandler, contextPool, checker);
                    break;
                case "GEAS_ori":
                    this.scheduler = new GEAS_ori(ruleHandler, contextPool, checker);
                    break;
                case "GEAS_opt_s":
                    this.scheduler = new GEAS_opt_s(ruleHandler, contextPool, checker);
                    break;
                case "GEAS_opt_c":
                    this.scheduler = new GEAS_opt_c(ruleHandler, contextPool, checker);
                    break;
                case "INFUSE_S":
                    this.scheduler = new INFUSE_S(ruleHandler, contextPool, checker);
                    break;
            }

            //check init
            this.checker.checkInit();
            logger.info("Init checking successfully.");
        }

        private void buildRulesAndPatterns() throws Exception {
            this.ruleHandler.buildRules(ruleFile);
            logger.info("Build rules successfully");
            this.patternHandler.buildPatterns(patternFile);
            logger.info("Build patterns successfully");

            for(Rule rule : ruleHandler.getRuleMap().values()){
                contextPool.PoolInit(rule);
                //S-condition
                rule.DeriveSConditions();
                //DIS
                rule.DeriveRCRESets();
            }

            for(String pattern_id : patternHandler.getPatternMap().keySet()){
                contextPool.ThreeSetsInit(pattern_id);
            }
        }

        private Object loadBfuncFile() {
            Path bfuncPath = Paths.get(bfuncFile).toAbsolutePath();
            Object bfuncInstance = null;
            try(URLClassLoader classLoader = new URLClassLoader(new URL[]{ bfuncPath.getParent().toFile().toURI().toURL()})){
                Class<?> c = classLoader.loadClass(bfuncPath.getFileName().toString().substring(0, bfuncPath.getFileName().toString().length() - 6));
                Constructor<?> constructor = c.getConstructor();
                bfuncInstance = constructor.newInstance();
            } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException |
                     IllegalAccessException | IOException e) {
                throw new RuntimeException(e);
            }
            return bfuncInstance;
        }

        @Override
        public Void call() throws Exception {
            DatagramSocket datagramSocket = null;
            try {
                datagramSocket = new DatagramSocket(6244);
                datagramSocket.setSoTimeout(10000);
            } catch (SocketException e) {
                logger.error("Fail to build datagramSocket.");
                e.printStackTrace();
            }
            logger.info("Build datagramSocket (localhost:6244) successfully.");
            logger.info("Checking starts at " + new Date(System.currentTimeMillis()));

            while(true){
                ContextChange contextChange = null;
                try {
                    oldTime_gen = System.currentTimeMillis();
                    contextChange = getNextChange(datagramSocket);
                    totalTime_gen += System.currentTimeMillis() - oldTime_gen;
                    long oldTime_chk = System.currentTimeMillis();
                    if(contextChange == null) break;
                    this.scheduler.doSchedule(contextChange);
                    totalTime_det += System.currentTimeMillis() - oldTime_chk;
                } catch (Exception e) {
                    logger.error("Fail to schedule \"" + contextChange +"\"");
                    e.printStackTrace();
                }
            }
            long oldTime_chk = System.currentTimeMillis();
            this.scheduler.checkEnds();
            totalTime_det += System.currentTimeMillis() - oldTime_chk;

            incsOutput();
            logger.info("Checking completes at " + new Date(System.currentTimeMillis()) );
            logger.info("TotalTime_gen: " + this.totalTime_gen + " ms\ttotalTime_det: " + this.totalTime_det + " ms\n");
            return null;
        }

        private ContextChange getNextChange(DatagramSocket datagramSocket){
            if(!changeQueue.isEmpty()){
                return changeQueue.poll();
            }
            assert datagramSocket != null;
            byte[] data = new byte[dataPacketLen];
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
            try {
                totalTime_gen += System.currentTimeMillis() - oldTime_gen;
                datagramSocket.receive(datagramPacket);
                oldTime_gen = System.currentTimeMillis();
                String line = new String(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength(), StandardCharsets.UTF_8);
                logger.info("Receive data: \"" + line.trim() + "\"");

                List<ContextChange> changeList = this.contextHandler.generateChanges(line.trim());
                changeQueue.addAll(changeList);
                return changeQueue.poll();
            } catch (IOException e) {
                // Buffer已经为空，且已经清空过，应该停止
                if(cleaned){
                    return null;
                }
                else{
                    logger.info("DatagramSocket timeout, stop receiving data");
                    try {
                        cleaned = true;
                        List<ContextChange> changeList = this.contextHandler.generateChanges(null);
                        changeQueue.addAll(changeList);
                        return changeQueue.poll();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            } catch (Exception e) {
                logger.error("\033[91m" + "Fail to generate changes" + "\033[0m");
                e.printStackTrace();
            }
            return null;
        }

        private void incsOutput() throws Exception {
            OutputStream outputStream = Files.newOutputStream(Paths.get(incOutFile));
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
            //对每个rule遍历
            for(Map.Entry<String, List<Map.Entry<Boolean, Set<Link>>>> entry : this.checker.getRuleLinksMap().entrySet()){
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(entry.getKey()).append('(');
                //累计每一次的link，氛围violated和satisfied(and or implies 两种都可能会有)
                Set<Link> accumVioLinks = new HashSet<>();
                Set<Link> accumSatLinks = new HashSet<>();
                for(Map.Entry<Boolean, Set<Link>> resultEntry : entry.getValue()){
                    if(resultEntry.getKey()){
                        accumSatLinks.addAll(resultEntry.getValue());
                    }
                    else{
                        accumVioLinks.addAll(resultEntry.getValue());
                    }
                }
                for(Link link : accumVioLinks){
                    Link.Link_Type linkType = Link.Link_Type.VIOLATED;
                    StringBuilder tmpBuilder = new StringBuilder(stringBuilder);
                    tmpBuilder.append(linkType.name()).append(",{");
                    //对当前每个link的变量赋值遍历
                    for(Map.Entry<String, Context> va : link.getVaSet()){
                        tmpBuilder.append("(").append(va.getKey()).append(",").append(Integer.parseInt(va.getValue().getCtx_id().substring(4)) + 1).append("),");
                    }
                    tmpBuilder.deleteCharAt(tmpBuilder.length() - 1);
                    tmpBuilder.append("})");
                    bufferedWriter.write(tmpBuilder.toString() + "\n");
                    bufferedWriter.flush();
                }
                for(Link link : accumSatLinks){
                    Link.Link_Type linkType = Link.Link_Type.SATISFIED;
                    StringBuilder tmpBuilder = new StringBuilder(stringBuilder);
                    tmpBuilder.append(linkType.name()).append(",{");
                    //对当前每个link的变量赋值遍历
                    for(Map.Entry<String, Context> va : link.getVaSet()){
                        tmpBuilder.append("(").append(va.getKey()).append(",").append(Integer.parseInt(va.getValue().getCtx_id().substring(4)) + 1).append("),");
                    }
                    tmpBuilder.deleteCharAt(tmpBuilder.length() - 1);
                    tmpBuilder.append("})");
                    bufferedWriter.write(tmpBuilder.toString() + "\n");
                    bufferedWriter.flush();
                }
            }

            bufferedWriter.close();
            outputStreamWriter.close();
            outputStream.close();
        }
    }

    static class CCEClient implements Callable<Void>{
        private final String dataFile;

        public CCEClient(String dataFile) {
            this.dataFile = dataFile;
        }

        @Override
        public Void call() throws Exception {
            //打开数据文件
            InputStream inputStream = Files.newInputStream(Paths.get(this.dataFile));
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.setSoTimeout(1000);
            datagramSocket.connect(InetAddress.getByName("localhost"), 10086);

            //循环读取change文件，发送数据
            long startTime_fake = -1;
            long startTime_real = -1;
            String line;
            int cnt = 0;
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

            logger.info("[CCEClient]: begin at: " + new Date(System.currentTimeMillis()));
            do {
                line = bufferedReader.readLine();
                if(line == null || line.equals("")){
                    break;
                }

                //TODO

                JSONObject recordJsonObj = JSON.parseObject(line.trim());
                long curTime_fake = simpleDateFormat.parse(recordJsonObj.getString("timestamp")).getTime();
                long curTime_real = System.currentTimeMillis();

                byte[] data = String.format("%-1024s", line).getBytes(StandardCharsets.UTF_8);
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
                if(startTime_fake == -1 && startTime_real == -1){
                    startTime_fake = curTime_fake;
                    startTime_real = curTime_real;
                    datagramSocket.send(datagramPacket);
                }
                else{
                    long deltaTime_real = curTime_real - startTime_real;
                    long deltaTime_fake = curTime_fake - startTime_fake;
                    while(deltaTime_real < deltaTime_fake){
                        deltaTime_real = System.currentTimeMillis() - startTime_real;
                    }
                    datagramSocket.send(datagramPacket);
                }
                cnt++;
                //System.out.println(System.currentTimeMillis() + " " + cnt);
                //if(cnt == 10000) break;
            }while(true);
            datagramSocket.close();
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();
            logger.info("[CCEClient]: " + new Date(System.currentTimeMillis()) + ": write over " + this.dataFile + " with " + cnt);
            return null;
        }
    }


    public OnlineStarter() {
    }

    public void start(String approach, String ruleFile, String bfuncFile, String patternFile, boolean isMG, String incOutFile, String runType){
       //FutureTask<Void> clientTask = new FutureTask<>(new CCEClient("./taxi/data_5_0-1_new.txt"));
        FutureTask<Void> serverTask = new FutureTask<>(new CCEServer(approach, ruleFile, bfuncFile, patternFile, isMG, incOutFile, runType));
        //new Thread(clientTask, "Client...").start();
        new Thread(serverTask, "Server...").start();
        try {
            //clientTask.get();
            serverTask.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
