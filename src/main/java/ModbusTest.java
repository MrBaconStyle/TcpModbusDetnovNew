//import net.wimpi.modbus.Modbus;
//import net.wimpi.modbus.io.ModbusTCPTransaction;
//import net.wimpi.modbus.msg.ReadInputRegistersRequest;
//import net.wimpi.modbus.msg.ReadInputRegistersResponse;
//import net.wimpi.modbus.net.TCPMasterConnection;
//import net.wimpi.modbus.procimg.InputRegister;
//import net.wimpi.modbus.util.BitVector;
//
//import java.net.InetAddress;
//import java.util.ArrayList;
//import java.util.List;
//
//public class ModbusTest {
//
//
//    public static void main(String[] args) {
//        String serverIp = "192.168.0.173"; // Replace with your Modbus server IP
//        int serverPort = 502; // Replace with the server's Modbus TCP port
//        int[] registerAddresses = {0x22}; // Replace with the desired register addresses
////        short[] ALARMS_IN_LOOP = {0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67};
////        int[] ALARM_ON_DETECTOR = {0x200, 0x300, 0x400, 0x600, 0x600, 0x700, 0x800, 0x900};
////        short[] LOOP_STATUS_ARRAY = {0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57};
//
//        try {
//            InetAddress serverAddress = InetAddress.getByName(serverIp);
//
//            TCPMasterConnection connection = new TCPMasterConnection(serverAddress);
//            connection.setPort(serverPort);
//            connection.connect();
//
//            int passCounter = 0;
//
//            while (true) {
//                System.out.println("Pass: " + passCounter);
//
//                for (int registerAddress : registerAddresses) {
//                    ReadInputRegistersRequest request = new ReadInputRegistersRequest(registerAddress, 1);
//
//                    ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
//                    transaction.setRequest(request);
//                    transaction.execute();
//
//                    ReadInputRegistersResponse response = (ReadInputRegistersResponse) transaction.getResponse();
//                    if (response != null) {
//                        InputRegister[] registers = response.getRegisters();
//                        for (InputRegister register : registers) {
//                            int value = register.getValue();
//                            System.out.println("Register HEX Value: " + value);
//                            String binValue = intToBinary(value);
//                            System.out.println("Register BIN Value: " + binValue);
//                        }
//                    } else {
//                        System.out.println("No response received.");
//                    }
//                }
//
//                passCounter++;
//                Thread.sleep(1000); // Delay between each pass (1 second in this example)
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static String intToBinary(int hexValue) {
//        String binaryString = Integer.toBinaryString(hexValue);
//        String paddedBinary = String.format("%8s", binaryString).replace(' ', '0');
//        return paddedBinary;
//    }
//}

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.wimpi.modbus.io.ModbusTCPTransaction;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadInputRegistersResponse;
import net.wimpi.modbus.net.TCPMasterConnection;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import java.net.URL;

public class ModbusTest {

    private static int PanelType = 0; // Global variable to store the number of loops

//    private static ObjectMapper objectMapper = new ObjectMapper(); // DODATO GLOBAL

    public static void main(String[] args) {
        String serverIp = "192.168.0.173"; // Replace with your Modbus server IP
        int serverPort = 502; // Replace with the server's Modbus TCP port
        int[] registerAddresses = {0x0, 0x1, 0x20, 0x21, 0x22, 0x25, 0x26, 0x27, 0x28, 0x2A, 0x2B};
        int[] loopArray = {0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67};
        int[] loopArrayFault = {0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77};
        int[] detectorArray = {0x200, 0x300, 0x400, 0x600, 0x600, 0x700, 0x800, 0x900};

        try {
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            TCPMasterConnection connection = new TCPMasterConnection(serverAddress);
            connection.setPort(serverPort);
            connection.connect();

            // Read PanelType from register 0x1 (assuming it's a single register)
            PanelType = readRegisterValue(connection, registerAddresses[1]);

            int passCounter = 0;

            while (true) {
                System.out.println("Pass: " + passCounter);

                // Read values of multiple registers
                int[] registerValues = readMultipleRegisters(connection, registerAddresses);

//                // Create a map to store register addresses and values
                Map<String, Integer> registerMap = new HashMap<>();
                List<String> alarms = new ArrayList<>();
                for (int i = 0; i < registerAddresses.length; i++) {
                    registerMap.put("0x" + Integer.toHexString(registerAddresses[i]), registerValues[i]);
                }

                // Check for alarms and get detectors in alarm
                List<String> detectorsInAlarm = getDetectorsInAlarm(connection, detectorArray, loopArray, registerMap);

                System.out.println(registerMap);

                // Create JSON string
                String jsonString = createJsonString(registerMap, detectorsInAlarm);

                // Send the JSON string to the PHP script
                sendJsonToPhp(jsonString);

                // Print values of each register
                for (int i = 0; i < registerAddresses.length; i++) {
                    System.out.println("Register 0x" + Integer.toHexString(registerAddresses[i]) +
                            " Value (HEX): " + Integer.toHexString(registerValues[i]) +
                            " Value (BIN): " + intToBinary8Bits(registerValues[i]) +
                            " Value (DEC): " + registerValues[i]);
                }

//                // Check the last bit of register 0x2B for Alarm pocetni radi kako treba
//                if (registerValues[10] >= 1) {
//                    // Check array elements based on PanelType
//                    int maxLoops = Math.min(PanelType, loopArray.length);
//                    for (int i = 0; i < maxLoops; i++) {
//                        int loopValueRegister = loopArray[i];
//                        int loopValue = readRegisterValue(connection, loopValueRegister);
//                        System.out.println("Loop " + (i + 1) + " have " + loopValue + " Alarm/s");
//
//                        // Check detectors in the loop if the loop is in alarm
//                        if (loopValue > 0) {
//                            // Assuming registerMap is a Map<String, Object> defined earlier in your code
//                            checkDetectorsInAlarm(connection, detectorArray[i], i + 1, loopValue);
//                        }
//                    }
//                }


//                // Provera Alarma - Novi radi listu
//                if (registerValues[10] >= 1) {
//                    // Check array elements based on PanelType
//                    int maxLoops = Math.min(PanelType, loopArray.length);
//                    List<String> allDetectorsInAlarm = new ArrayList<>();
//
//                    for (int i = 0; i < maxLoops; i++) {
//                        int loopValueRegister = loopArray[i];
//                        int loopValue = readRegisterValue(connection, loopValueRegister);
//
//                        // Check detectors in the loop if the loop is in alarm
//                        if (loopValue > 0) {
//                            List<String> detectorsInAlarm = checkDetectorsInAlarm(connection, detectorArray[i], i + 1, loopValue);
//
//                            // Add detectorsInAlarm to the allDetectorsInAlarm list
//                            allDetectorsInAlarm.addAll(detectorsInAlarm);
//                        }
//                    }
//
//                    // Print all detectors in alarm as a single string
//                    if (!allDetectorsInAlarm.isEmpty()) {
//                        System.out.println("Detectors in Alarm: " + String.join(", ", allDetectorsInAlarm));
//                    }
//                }


//                // Check the last bit of register 0x2A and 0x21 for removed detector
//                if ((registerValues[8] >= 1) && (registerValues[2] >= 11)) {
//                    // Check array elements based on PanelType
//                    int maxLoops = Math.min(PanelType, loopArrayFault.length);
//                    for (int i = 0; i < maxLoops; i++) {
//                        int loopValueRegister = loopArrayFault[i];
//                        int loopValue = readRegisterValue(connection, loopValueRegister);
//                        System.out.println("Loop " + (i + 1) + " have " + loopValue + " Fault/s");
//
//                        // Check detectors in the loop if the loop is in fault
//                        if (loopValue > 0) {
//                            checkDetectorsInFault(connection, detectorArray[i], i + 1, loopValue);
//                        }
//                    }
//                }

                passCounter++;
                Thread.sleep(1000); // Delay between each pass (1 second in this example)
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int[] readMultipleRegisters(TCPMasterConnection connection, int[] registerAddresses) {
        int[] registerValues = new int[registerAddresses.length];

        try {
            for (int i = 0; i < registerAddresses.length; i++) {
                registerValues[i] = readRegisterValue(connection, registerAddresses[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return registerValues;
    }

    public static int readRegisterValue(TCPMasterConnection connection, int registerAddress) {
        int registerValue = 0;

        try {
            ReadInputRegistersRequest request = new ReadInputRegistersRequest(registerAddress, 1);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            ReadInputRegistersResponse response = (ReadInputRegistersResponse) transaction.getResponse();
            if (response != null) {
                registerValue = response.getRegisterValue(0);
            } else {
                System.out.println("No response received for register 0x" + Integer.toHexString(registerAddress));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return registerValue;
    }

    public static String intToBinary8Bits(int hexValue) {
        String binaryString = Integer.toBinaryString(hexValue);
        return String.format("%8s", binaryString).replace(' ', '0');
    }

//    //     Klasicna petlja za proveru alarma
//    public static void checkDetectorsInAlarm(TCPMasterConnection connection, int startRegister, int loopIndex, int loopValue) {
//        try {
//            List<Integer> detectorsInAlarm = new ArrayList<>();
//            int maxDetectors = 256; // Assuming the maximum number of detectors in a loop is 256
//
//            for (int i = 0; i < maxDetectors; i++) {
//                int detectorRegister = startRegister + i;
//                int detectorValue = readRegisterValue(connection, detectorRegister);
//
////                System.out.println("Checking: Loop " + loopIndex + "; Detector " + String.format("%03d", i + 1) +
////                        " Value: " + detectorValue);
//
//                if (detectorValue == 2) { // Adjusted for value 2 indicating alarm
//                    detectorsInAlarm.add(i + 1); // Adding detector number (1-based) to the list
//
//                    // Stop checking detectors if the required number of alarms is found
//                    if (detectorsInAlarm.size() == loopValue) {
//                        break;
//                    }
//                }
//            }
//
//            // Print detectors in alarm for the loop if any
//            if (!detectorsInAlarm.isEmpty()) {
//                System.out.println("Alarms: Loop " + loopIndex + "; Detectors in Alarm: " + detectorsInAlarm);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public static List<String> checkDetectorsInAlarm(TCPMasterConnection connection, int startRegister, int loopIndex, int loopValue) {
        List<String> detectorsInAlarm = new ArrayList<>();
        int maxDetectors = 256; // Assuming the maximum number of detectors in a loop is 256

        for (int i = 0; i < maxDetectors; i++) {
            int detectorRegister = startRegister + i;
            int detectorValue = readRegisterValue(connection, detectorRegister);

            if (detectorValue == 2) { // Adjusted for value 2 indicating alarm
                detectorsInAlarm.add(loopIndex + "." + (1 + i)); // LoopIndex.detectorAddress

                // Stop checking detectors if the required number of alarms is found
                if (detectorsInAlarm.size() == loopValue) {
                    break;
                }
            }
        }

        return detectorsInAlarm;
    }

    private static List<String> getDetectorsInAlarm(TCPMasterConnection connection, int[] detectorArray, int[] loopArray, Map<String, Integer> registerMap) {
        List<String> detectorsInAlarm = new ArrayList<>();

        // Iterate through loops and check detectors in alarm
        int maxLoops = Math.min(PanelType, loopArray.length);
        for (int i = 0; i < maxLoops; i++) {
            int loopValueRegister = loopArray[i];
            int loopValue = readRegisterValue(connection, loopValueRegister);

            // Check detectors in the loop if the loop is in alarm
            if (loopValue > 0) {
                List<String> detectorsInLoop = checkDetectorsInAlarm(connection, detectorArray[i], i + 1, loopValue);

                // Add detectors in loop to the main list
                detectorsInAlarm.addAll(detectorsInLoop);
            }
        }

        return detectorsInAlarm;
    }

    public static void checkDetectorsInFault(TCPMasterConnection connection, int startRegister, int loopIndex, int loopValue) {
        try {
            List<Integer> detectorsInFault = new ArrayList<>();
            int maxDetectors = 256; // Assuming the maximum number of detectors in a loop is 256

            for (int i = 0; i < maxDetectors; i++) {
                int detectorRegister = startRegister + i;
                int detectorValue = readRegisterValue(connection, detectorRegister);

                if (detectorValue == 1) { // Adjusted for value 1 indicating fault
                    detectorsInFault.add(i + 1); // Adding detector number (1-based) to the list

                    // Stop checking detectors if the required number of alarms is found
                    if (detectorsInFault.size() == loopValue) {
                        break;
                    }
                }
            }

            // Print detectors in alarm for the loop if any
            if (!detectorsInFault.isEmpty()) {
                System.out.println("Faults: Loop " + loopIndex + "; Detectors in Alarm: " + detectorsInFault);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    // Prva Json metoda
//    private static String createJsonString(Map<String, Integer> registerMap) {
//        StringBuilder jsonBuilder = new StringBuilder();
//        jsonBuilder.append("{\"registers\":[");
//
//        for (Map.Entry<String, Integer> entry : registerMap.entrySet()) {
//            jsonBuilder.append("{\"address\":\"").append(entry.getKey()).append("\",\"value\":").append(entry.getValue()).append("},");
//        }
//
//        // Remove the trailing comma
//        if (jsonBuilder.charAt(jsonBuilder.length() - 1) == ',') {
//            jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
//        }
//
//        jsonBuilder.append("]}");
//
//        return jsonBuilder.toString();
//    }

//    // Pravljenje JSONA Statama sa REG i ALARMIMA
//    private static String createJsonString(Map<String, Integer> registerMap, List<String> detectorsInAlarm) {
//        StringBuilder jsonBuilder = new StringBuilder();
//        jsonBuilder.append("{\"registers\":[");
//
//        // Populate registers array
//        for (Map.Entry<String, Integer> entry : registerMap.entrySet()) {
//            jsonBuilder.append("{\"address\":\"").append(entry.getKey()).append("\",\"value\":").append(entry.getValue()).append("},");
//        }
//
//        // Remove the trailing comma
//        if (jsonBuilder.charAt(jsonBuilder.length() - 1) == ',') {
//            jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
//        }
//
//        jsonBuilder.append("],\"alarms\":[");
//
//        // Populate alarms array
//        for (String detector : detectorsInAlarm) {
//            jsonBuilder.append("{\"point\":\"").append(detector).append("\"},");
//        }
//
//        // Remove the trailing comma
//        if (jsonBuilder.charAt(jsonBuilder.length() - 1) == ',') {
//            jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
//        }
//
//        jsonBuilder.append("]}");
//
//        return jsonBuilder.toString();
//    }

    private static String createJsonString(Map<String, Integer> registerMap, List<String> alarms) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"registers\":[");

        // Process registers
        for (Map.Entry<String, Integer> entry : registerMap.entrySet()) {
            jsonBuilder.append("{\"address\":\"").append(entry.getKey()).append("\",\"value\":").append(entry.getValue()).append("},");
        }

        // Remove the trailing comma
        if (jsonBuilder.charAt(jsonBuilder.length() - 1) == ',') {
            jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
        }

        // Process alarms
        jsonBuilder.append("],\"alarms\":[");

        for (String alarm : alarms) {
            jsonBuilder.append("{\"point\":\"").append(alarm).append("\"},");
        }

        // Remove the trailing comma
        if (jsonBuilder.charAt(jsonBuilder.length() - 1) == ',') {
            jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
        }

        jsonBuilder.append("]}");

        return jsonBuilder.toString();
    }



    private static void sendJsonToPhp(String jsonData) throws Exception {
        // Specify your PHP script URL
        String phpScriptUrl = "http://192.168.0.31/save.php";

        // Create a URL object
        URL url = new URL(phpScriptUrl);

        // Open a connection to the URL
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set the request method to POST
        connection.setRequestMethod("POST");

        // Enable input/output streams
        connection.setDoOutput(true);

        // Specify content type as JSON
        connection.setRequestProperty("Content-Type", "application/json");

        // Get the output stream of the connection
        try (OutputStream os = connection.getOutputStream()) {
            // Write the JSON data to the output stream
            os.write(jsonData.getBytes("UTF-8"));
        }

        // Get the response code from the connection
        int responseCode = connection.getResponseCode();
        System.out.println("HTTP Response Code: " + responseCode);

        // Close the connection
        connection.disconnect();
    }
}