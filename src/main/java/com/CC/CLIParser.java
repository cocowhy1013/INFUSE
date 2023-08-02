package com.CC;

import com.CC.Util.Loggable;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;


public class CLIParser implements Loggable {

    public static List<String> legalApproaches = new ArrayList<>(){{
        add("ECC+IMD");
        add("ECC+GEAS_ori");
        add("PCC+IMD");
        add("PCC+GEAS_ori");
        add("ConC+IMD");
        add("ConC+GEAS_ori");
        //add("INFUSE_base");
        add("INFUSE");
    }};


    public static String testIncOut = "inconsistencies.json";
    public static String testCCTOut = "cct.txt";
    public static String incOut = "inconsistencies.txt";
    public static String dataOut = "fixedData.json";

    public static void main(String[] args) throws Exception {
        // common
        Option opt_ap = Option.builder("approach")
                .argName("approach")
                .hasArg()
                .required(false)
                .desc("Use the specified approach for checking [ECC+IMD/ECC+GEAS_ori/PCC+IMD/PCC+GEAS_ori/ConC+IMD/ConC+GEAS_ori/INFUSE_base/INFUSE]")
                .build();

        Option opt_rf = Option.builder("rules")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Load rules from given file (XML file)")
                .build();

        Option opt_bf = Option.builder("bfuncs")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Load bfunctions from given file (Class file)")
                .build();


        Option opt_mg = new Option("mg", false, "Enable link generation minimization");
        opt_mg.setRequired(false);

        Option opt_h = new Option("help", false, "Print the usage");
        opt_h.setRequired(false);

        Option opt_oi = Option.builder("incs")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Write detected inconsistencies to given file")
                .build();

        // normal run
        Option opt_md = Option.builder("mode")
                .argName("mode")
                .hasArg()
                .required(false)
                .desc("Run under the given mode [offline/online]")
                .build();

        Option opt_pf = Option.builder("patterns")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Load patterns from given file (XML file)")
                .build();

        Option opt_mf = Option.builder("mfuncs")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Load mfunctions from given file (Class file)")
                .build();

        Option opt_df = Option.builder("data")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Read data from given file")
                .build();

        Option opt_dt = Option.builder("datatype")
                .argName("type")
                .hasArg()
                .required(false)
                .desc("Specify the type of data in dataFile [rawData/change]")
                .build();

        Option opt_od = Option.builder("fixeddata")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Write fixed data to given file (JSON file)")
                .build();

        // testing run
        Option opt_t = new Option("test",false,"Test the checking engine");
        opt_t.setRequired(false);

        Option opt_cp = Option.builder("contextpool")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Read context pool from given file for testing (JSON file)")
                .build();

        Option opt_oc = Option.builder("cct")
                .argName("file")
                .hasArg()
                .required(false)
                .desc("Write CCT to given file for testing (TXT file)")
                .build();

        Options options = new Options();
        options.addOption(opt_h);
        options.addOption(opt_t);
        options.addOption(opt_rf);
        options.addOption(opt_pf);
        options.addOption(opt_df);
        options.addOption(opt_ap);
        options.addOption(opt_md);
        options.addOption(opt_mf);
        options.addOption(opt_bf);
        options.addOption(opt_dt);
        options.addOption(opt_cp);
        options.addOption(opt_mg);
        options.addOption(opt_oi);
        options.addOption(opt_oc);
        //options.addOption(opt_od);

        CommandLine cli = null;
        CommandLineParser cliParser = new DefaultParser();
        HelpFormatter helpFormatter = new HelpFormatter();

        try {
            cli = cliParser.parse(options, args);
        } catch (ParseException e) {
            helpFormatter.printHelp("java -jar INFUSE-version.jar [Options]", options, true);
            e.printStackTrace();
        }

        assert cli != null;
        if(cli.hasOption("help")){
            helpFormatter.printHelp("java -jar INFUSE-version.jar [Options]", options);
        }
        else if(cli.hasOption("test")){
/*
    java -jar INFUSE.jar
    -test
    -approach INFUSE
    -rules rules.xml
    -bfuncs bfuncs.class
    -contextpool cp.json
    -mg
    -incs incs.json
    -cct cct.txt
 */
            // checking approach
            String approach = null;
            if(!cli.hasOption("approach")){
                logger.error("\033[91m" + "No specified approach, please use option \"-approach \", available approaches: [ECC+IMD/ECC+GEAS_ori/PCC+IMD/PCC+GEAS_ori/ConC+IMD/ConC+GEAS_ori/INFUSE]" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                approach = cli.getOptionValue("approach");
                if(!legalApproaches.contains(approach)){
                    logger.error("\033[91m" + "The approach is illegal, available approaches: [ECC+IMD/ECC+GEAS_ori/PCC+IMD/PCC+GEAS_ori/ConC+IMD/ConC+GEAS_ori/INFUSE]" + "\033[0m");
                    logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                    System.exit(1);
                }
            }
            // rule file
            String ruleFile = null;
            if(!cli.hasOption("rules")){
                logger.error("\033[91m" + "No specified rule file, please use option \"-rules\"" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                ruleFile = cli.getOptionValue("rules");
                logger.info(String.format("The rule file is \"%s\"", ruleFile));
            }
            // bfunc file
            String bfuncFile = null;
            if(!cli.hasOption("bfuncs")){
                logger.error("\033[91m" + "No specified bfunction file, please use option \"-bfuncs\"" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                bfuncFile = cli.getOptionValue("bfuncs");
                logger.info(String.format("The bfunction file is \"%s\"", bfuncFile));
            }
            // context pool file
            String contextPool = null;
            if(!cli.hasOption("contextpool")){
                logger.error("\033[91m" + "No specified context pool, please use option \"contextpool\"" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                contextPool = cli.getOptionValue("contextpool");
                logger.info(String.format("The context pool is \"%s\"", contextPool));
            }
            // isMG or not
            boolean isMG = cli.hasOption("mg");
            logger.info(String.format("Minimizing link generation is %s", isMG ? "on" : "off"));
            // incs
            String incs = null;
            if(!cli.hasOption("incs")){
                incs = testIncOut;
                logger.info("The default inconsistency file is \"" + testIncOut + "\"");
            }
            else{
                incs = cli.getOptionValue("incs");
                logger.info("The inconsistency file is \"" + incs + "\"");
            }
            // cct
            String cct = null;
            if(!cli.hasOption("cct")){
                cct = testCCTOut;
                logger.info("The default cct file is \"" + testCCTOut + "\"");
            }
            else{
                cct = cli.getOptionValue("cct");
                logger.info("The cct file is \"" + cct + "\"");
            }

            String parentPathStr = testModeDataConvertor(contextPool);
            String patternFile = parentPathStr + "/tmpPatterns.xml";
            String dataFile = parentPathStr + "/tmpData.txt";

            //default offline checking
            long startTime = System.nanoTime();
            OfflineStarter offlineStarter = new OfflineStarter();
            offlineStarter.start(approach, ruleFile, bfuncFile, patternFile, null, dataFile, "change", isMG, incs, cct, "test");
            long totalTime = System.nanoTime() - startTime;

//            Files.delete(Paths.get(patternFile));
//            Files.delete(Paths.get(dataFile));

            logger.info("Detected inconsistencies is in \"" + incs + "\" and CCT is in \"" + cct + "\"");
            logger.info("Checking Approach: " + approach + "\t" + totalTime / 1000000L + " ms");
        }
        else {
/*
java -jar INFUSE.jar
-mode offline
-approach INFUSE
-rules rules.xml
-bfuncs bfuncs.class
-patterns patterns.xml
-mfuncs mfuncs.class
-data data.txt
-datatype rawData
-mg
-incs incs.json
//-fixeddata fiexeddata.txt
 */
            // checking mode
            String checkingMode = null;
            if(!cli.hasOption("mode")){
                logger.error("\033[91m" + "No specified mode, please use option \"-mode\", available modes: [offline/online]" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                checkingMode = cli.getOptionValue("mode");
                if(!checkingMode.equalsIgnoreCase("offline") && !checkingMode.equalsIgnoreCase("online")){
                    logger.error("\033[91m" + "The mode is illegal, available modes: [offline/online]" + "\033[0m");
                    logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                    System.exit(1);
                }
            }
            // checking approach
            String approach = null;
            if(!cli.hasOption("approach")){
                logger.error("\033[91m" + "No specified approach, please use option \"-approach \", available approaches: [ECC+IMD/ECC+GEAS_ori/PCC+IMD/PCC+GEAS_ori/ConC+IMD/ConC+GEAS_ori/INFUSE]" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                approach = cli.getOptionValue("approach");
                if(!legalApproaches.contains(approach)){
                    logger.error("\033[91m" + "The approach is illegal, available approaches: [ECC+IMD/ECC+GEAS_ori/PCC+IMD/PCC+GEAS_ori/ConC+IMD/ConC+GEAS_ori/INFUSE]" + "\033[0m");
                    logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                    System.exit(1);
                }
            }
            // rule file
            String ruleFile = null;
            if(!cli.hasOption("rules")){
                logger.error("\033[91m" + "No specified rule file, please use option \"-rules\"" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                ruleFile = cli.getOptionValue("rules");
                logger.info(String.format("The rule file is \"%s\"", ruleFile));
            }
            // bfunc file
            String bfuncFile = null;
            if(!cli.hasOption("bfuncs")){
                logger.error("\033[91m" + "No specified bfunction file, please use option \"-bfuncs\"" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                bfuncFile = cli.getOptionValue("bfuncs");
                logger.info(String.format("The bfunction file is \"%s\"", bfuncFile));
            }
            // pattern file
            String patternFile = null;
            if(!cli.hasOption("patterns")){
                logger.error("\033[91m" + "No specified pattern file, please use option \"-patterns\"" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                patternFile = cli.getOptionValue("patterns");
                logger.info(String.format("The pattern file is \"%s\"", patternFile));
            }
            // mfunc file
            String mfuncFile = null;
            if(!cli.hasOption("mfuncs")){
                logger.info("No specified mfunction file");
            }
            else{
                mfuncFile = cli.getOptionValue("mfuncs");
                logger.info(String.format("The mfunction file is \"%s\"", mfuncFile));
            }
            // data file [offline]
            String dataFile = null;
            if(checkingMode.equalsIgnoreCase("offline")){
                //data file
                if(!cli.hasOption("data")){
                    logger.error("\033[91m" + "No specified data file in offline mode, please use option \"-data\"" + "\033[0m");
                    logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                    System.exit(1);
                }
                else{
                    dataFile = cli.getOptionValue("data");
                    logger.info(String.format("The data file is \"%s\"", dataFile));
                }
            }
            else{
                if(cli.hasOption("data")){
                    logger.error("\033[91m" + "Cannot specify data file in online mode" + "\033[0m");
                    logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                    System.exit(1);
                }
            }
            // data type
            String dataType = null;
            if(!cli.hasOption("datatype")){
                logger.error("\033[91m" + "No specified data type, please use option \"-datatype\", available datatypes: [rawData/change]" + "\033[0m");
                logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                System.exit(1);
            }
            else{
                dataType = cli.getOptionValue("datatype");
                if(!dataType.equals("rawData") && !dataType.equals("change")){
                    logger.error("\033[91m" + "The data type is illegal, available datatypes: [rawData/change]" + "\033[0m");
                    logger.info("\033[92m" + "Use option \"-help\" for more information"  + "\033[0m");
                    System.exit(1);
                }
            }
            // isMG or not
            boolean isMG = cli.hasOption("mg");
            logger.info(String.format("Minimizing link generation is %s", isMG ? "on" : "off"));
            // incs
            String incs = null;
            if(!cli.hasOption("incs")){
                incs = incOut;
                logger.info("The default inconsistency file is \"" + incOut + "\"");
            }
            else{
                incs = cli.getOptionValue("incs");
                logger.info(String.format("The inconsistency file is \"%s\"", incs));
            }
//            // fixedData
//            String fixedData = null;
//            if(!cli.hasOption("fixeddata")){
//                fixedData = dataOut;
//                logger.info("\033[92m" + "The default fixed data file is \"" + dataOut + "\"\033[0m");
//            }
//            else{
//                fixedData = cli.getOptionValue("fixeddata");
//            }

            // start
            if(checkingMode.equalsIgnoreCase("offline")){
                long startTime = System.nanoTime();
                OfflineStarter offlineStarter = new OfflineStarter();
                offlineStarter.start(approach, ruleFile, bfuncFile, patternFile, mfuncFile, dataFile, dataType, isMG, incs, null, "run");
                long totalTime = System.nanoTime() - startTime;
                logger.info("\033[92m" + "Time cost: " + totalTime / 1000000L + " ms\033[0m");
            }
            else if(checkingMode.equalsIgnoreCase("online")){
                OnlineStarter onlineStarter = new OnlineStarter();
                onlineStarter.start(approach, ruleFile, bfuncFile, patternFile, mfuncFile, dataType, isMG, incs, null);
            }
        }
    }

    /*
    // version 1
    private static String testModeDataConvertor(String contextPool) throws Exception {
        Path cpPath = Paths.get(contextPool).toAbsolutePath();
        String parent = cpPath.getParent().toString();

        OutputStream patternOutputStream = Files.newOutputStream(Paths.get(parent+ "/tmpPatterns.xml"));
        OutputStreamWriter patternWriter = new OutputStreamWriter(patternOutputStream, StandardCharsets.UTF_8);
        BufferedWriter patternBufferWriter = new BufferedWriter(patternWriter);
        patternBufferWriter.write("<?xml version=\"1.0\"?>\n\n<patterns>\n\n");

        OutputStream dataOutputStream = Files.newOutputStream(Paths.get(parent + "/tmpData.txt"));
        OutputStreamWriter dataWriter = new OutputStreamWriter(dataOutputStream, StandardCharsets.UTF_8);
        BufferedWriter dataBufferWriter = new BufferedWriter(dataWriter);

        String cpStr = FileUtils.readFileToString(new File(contextPool), StandardCharsets.UTF_8);
        JSONArray cpArray = (JSONArray) JSON.parse(cpStr);
        for(Object patObj : cpArray){
            JSONObject patJsonObj = (JSONObject) patObj;
            String patternId = patJsonObj.getString("pat_id");
            String patternStrBuilder = "<pattern>\n<id>" + patternId +
                    "</id>\n" + "<freshness>\n" + "<type>number</type>\n" +
                    "<value>1</value>\n" + "</freshness>" + "</pattern>\n\n";
            patternBufferWriter.write(patternStrBuilder);

            JSONArray ctxJsonArray = patJsonObj.getJSONArray("contexts");
            for(Object ctxObj : ctxJsonArray){
                JSONObject ctxJsonObj = (JSONObject) ctxObj;
                String ctxId = ctxJsonObj.getString("ctx_id");

                JSONObject lineDataJsonObj = new JSONObject();
                lineDataJsonObj.put("changeType", "+");
                lineDataJsonObj.put("patternId", patternId);
                JSONObject newCtxJsonObj = new JSONObject();
                newCtxJsonObj.put("contextId", ctxId);
                newCtxJsonObj.put("fields", ctxJsonObj.getJSONObject("fields"));
                lineDataJsonObj.put("context", newCtxJsonObj);

                dataBufferWriter.write(lineDataJsonObj.toJSONString() + "\n");
            }
        }

        dataBufferWriter.flush();
        patternBufferWriter.write("</patterns>\n");
        patternBufferWriter.flush();

        dataOutputStream.close();
        dataBufferWriter.close();
        dataWriter.close();

        patternOutputStream.close();
        patternBufferWriter.close();
        patternWriter.close();

        return parent;
    }
     */

    // version 2
    private static String testModeDataConvertor(String contextPool) throws Exception {
        Path cpPath = Paths.get(contextPool).toAbsolutePath();
        String parent = cpPath.getParent().toString();

        OutputStream patternOutputStream = Files.newOutputStream(Paths.get(parent+ "/tmpPatterns.xml"));
        OutputStreamWriter patternWriter = new OutputStreamWriter(patternOutputStream, StandardCharsets.UTF_8);
        BufferedWriter patternBufferWriter = new BufferedWriter(patternWriter);
        patternBufferWriter.write("<?xml version=\"1.0\"?>\n\n<patterns>\n\n");

        OutputStream dataOutputStream = Files.newOutputStream(Paths.get(parent + "/tmpData.txt"));
        OutputStreamWriter dataWriter = new OutputStreamWriter(dataOutputStream, StandardCharsets.UTF_8);
        BufferedWriter dataBufferWriter = new BufferedWriter(dataWriter);

        String cpStr = FileUtils.readFileToString(new File(contextPool), StandardCharsets.UTF_8);
        JSONArray cpArray = (JSONArray) JSON.parse(cpStr);
        //cpArray = shuffleJSONArray(cpArray);
        for(Object patObj : cpArray){
            JSONObject patJsonObj = (JSONObject) patObj;
            String patternId = patJsonObj.getString("pat_id");

            //generate pattern
            String patternStrBuilder = "<pattern>\n<id>" + patternId +
                    "</id>\n" + "<freshness>\n" + "<type>number</type>\n" +
                    "<value>1</value>\n" + "</freshness>" + "</pattern>\n\n";
            patternBufferWriter.write(patternStrBuilder);

            //generate context changes for one pattern

            // 1. add fake element 0
            JSONObject addFakeElementJsonObj = new JSONObject();
            addFakeElementJsonObj.put("changeType", "+");
            addFakeElementJsonObj.put("patternId", patternId);
            JSONObject fakeElementJsonObj = new JSONObject();
            fakeElementJsonObj.put("contextId", "0");
            JSONObject fieldJsonObj = new JSONObject();
            fieldJsonObj.put("data", "0");
            fakeElementJsonObj.put("fields", fieldJsonObj);
            addFakeElementJsonObj.put("context", fakeElementJsonObj);
            dataBufferWriter.write(addFakeElementJsonObj.toJSONString() + "\n");

            // 2. add origin elements
            List<JSONObject> originChanges = new ArrayList<>();
            JSONArray ctxJsonArray = patJsonObj.getJSONArray("contexts");
            for(Object ctxObj : ctxJsonArray){
                JSONObject ctxJsonObj = (JSONObject) ctxObj;
                String ctxId = ctxJsonObj.getString("ctx_id");

                JSONObject lineDataJsonObj = new JSONObject();
                lineDataJsonObj.put("changeType", "+");
                lineDataJsonObj.put("patternId", patternId);
                JSONObject newCtxJsonObj = new JSONObject();
                newCtxJsonObj.put("contextId", ctxId);
                newCtxJsonObj.put("fields", ctxJsonObj.getJSONObject("fields"));
                lineDataJsonObj.put("context", newCtxJsonObj);

                dataBufferWriter.write(lineDataJsonObj.toJSONString() + "\n");
                originChanges.add(lineDataJsonObj);
            }

            // 3. remove fake element 0
            addFakeElementJsonObj.replace("changeType", "-");
            dataBufferWriter.write(addFakeElementJsonObj.toJSONString() + "\n");

            // 4. remove origin elements
            for(JSONObject jsonObject : originChanges){
                jsonObject.replace("changeType", "-");
                dataBufferWriter.write(jsonObject.toJSONString() + "\n");
            }

            // 5. add origin elements
            for(JSONObject jsonObject : originChanges){
                jsonObject.replace("changeType", "+");
                dataBufferWriter.write(jsonObject.toJSONString() + "\n");
            }

        }


        dataBufferWriter.flush();
        patternBufferWriter.write("</patterns>\n");
        patternBufferWriter.flush();

        dataOutputStream.close();
        dataBufferWriter.close();
        dataWriter.close();

        patternOutputStream.close();
        patternBufferWriter.close();
        patternWriter.close();

        return parent;
    }

    private static JSONArray shuffleJSONArray(JSONArray jsonArray){
        JSONArray shuffledArray = new JSONArray();
        List<Integer> indexList = new ArrayList<>();
        for(int i = 0; i < jsonArray.size(); ++i){
            indexList.add(i);
        }
        Collections.shuffle(indexList);
        for(int index : indexList){
            shuffledArray.add(jsonArray.get(index));
        }
        return shuffledArray;
    }
}

