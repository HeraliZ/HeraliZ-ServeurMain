package com.Spartan.Utils;

import java.security.MessageDigest;
import oshi.SystemInfo;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;

public class ComponentInfo {
    public static String getSystemUUID() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        ComputerSystem cs = hal.getComputerSystem();
        return cs.getHardwareUUID();
    }

    public static String getHWID() {
        try {
            String computerName = System.getenv("COMPUTERNAME");
            String processorId = System.getenv("PROCESSOR_IDENTIFIER");
            String processorLevel = System.getenv("PROCESSOR_LEVEL");
            // Ces variables n'existent que sous Windows. Ailleurs, le hash serait
            // identique pour tous les joueurs de même nom de session : inexploitable.
            if (computerName == null || computerName.isBlank() || processorId == null || processorLevel == null) {
                return "unknown";
            }
            String toEncrypt = computerName
                + System.getProperty("user.name")
                + processorId
                + processorLevel;
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(toEncrypt.getBytes());
            StringBuffer hexString = new StringBuffer();
            byte[] byteData = md.digest();

            for (byte aByteData : byteData) {
                String hex = Integer.toHexString(0xFF & aByteData);
                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }
}
