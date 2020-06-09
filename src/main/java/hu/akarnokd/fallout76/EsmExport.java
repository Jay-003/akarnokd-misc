package hu.akarnokd.fallout76;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.zip.Inflater;

import com.google.gson.*;

public class EsmExport {

    static PrintWriter saveIds;
    
    static Map<Integer, String> edidMap;
    
    static Set<Integer> usedFormIDs;
    
    static Map<Integer, Float> globalValues;

    static PrintWriter leveledList;
    
    static Map<Integer, String> curveTables;

    public static void main(String[] args) throws Throwable {
        File file = new File(
                "e:\\Games\\Fallout76\\Data\\SeventySix.esm");

        edidMap = new HashMap<>(100_000);
        usedFormIDs = new HashSet<>(10_000);
        globalValues = new HashMap<>(10_000);
        curveTables = new HashMap<>(1000);
        
        String lvliFile = "e:\\Games\\Fallout76\\Data\\Dump\\SeventySix_LVLIs.js";

        leveledList = new PrintWriter(new FileWriter(
                lvliFile));
        leveledList.println("leveledLists = {");

        try {
            saveIds = new PrintWriter(new FileWriter(
                    "e:\\Games\\Fallout76\\Data\\Dump\\SeventySix_EDIDs.txt"));
            
            
            try {
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    while (raf.getFilePointer() < raf.length()) {
                        processTopGroups(raf, "LVLI,GLOB,CURV");
                    }
                }
            } finally {
                saveIds.close();
            }
        } finally {
            leveledList.println("}");
            leveledList.close();
            
            // fix the trailing commas
            List<String> lines = Files.readAllLines(Paths.get(lvliFile));
            
            for (int i = 0; i < lines.size() - 1; i++) {
                String line1 = lines.get(i);
                String line2 = lines.get(i + 1).trim();
                
                if (line1.endsWith(",") && 
                        (line2.startsWith("}") || line2.startsWith("]"))) {
                    lines.set(i, line1.substring(0, line1.length() - 1));
                }
            }
            
            Files.write(Paths.get(lvliFile), lines);
        }
        
        for (Integer id : usedFormIDs) {
            if (!edidMap.containsKey(id)) {
                System.err.printf("%08X missing edid%n", id);
            }
        }
        
        System.out.println("usedFormIDs before: " + usedFormIDs.size());
        System.out.println("EDIDs before: " + edidMap.size());
        System.out.println("GLOBs before: " + globalValues.size());
        System.out.println("CURVs before: " + curveTables.size());
        // remove unneeded references
        edidMap.keySet().retainAll(usedFormIDs);
        globalValues.keySet().retainAll(usedFormIDs);
        //curveTables.keySet().retainAll(usedFormIDs);
        System.out.println("EDIDs after: " + edidMap.size());
        System.out.println("GLOBs after: " + globalValues.size());
        System.out.println("CURVs after: " + curveTables.size());
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                "e:\\Games\\Fallout76\\Data\\Dump\\SeventySix_EDIDs.js"))) {
            pw.println("edids = {");
            for (Map.Entry<Integer, String> e : edidMap.entrySet()) {
                pw.print("\"");
                pw.printf("%08X", e.getKey());
                pw.print("\": \"");
                pw.print(e.getValue().replace("\"", "\\\""));
                pw.println("\",");
            }
            pw.println("}");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(
                "e:\\Games\\Fallout76\\Data\\Dump\\SeventySix_GLOBs.js"))) {
            pw.println("globals = {");
            for (Map.Entry<Integer, Float> e : globalValues.entrySet()) {
                pw.print("\"");
                pw.printf("%08X", e.getKey());
                pw.print("\": ");
                pw.print(e.getValue());
                pw.println(",");
            }
            pw.println("}");
        }

        Map<String, Ba2FileEntry> curveMap = new HashMap<>();

        loadBa2(curveMap, "e:\\Games\\Fallout76\\Data\\SeventySix - Startup.ba2");
        loadBa2(curveMap, "e:\\Games\\Fallout76\\Data\\SeventySix - MiscClient.ba2");

        try (PrintWriter pw = new PrintWriter(new FileWriter(
                "e:\\Games\\Fallout76\\Data\\Dump\\SeventySix_CURVs.js"))) {
            pw.println("curves = {");
            for (Map.Entry<Integer, String> e : curveTables.entrySet()) {
                String ckey = e.getValue().toLowerCase().replace('\\', '/');

                Ba2FileEntry ba2Entry = curveMap.get(ckey);
                if (ba2Entry == null) {
                    ba2Entry = curveMap.get("misc/curvetables/json/" + ckey);
                }
                if (ba2Entry == null) {
                    System.err.printf("Unknown curve table: %08X - %s%n", e.getKey(), e.getValue());
                } else {
                    JsonElement obj = new JsonParser().parse(new String(ba2Entry.data, StandardCharsets.ISO_8859_1));

                    pw.print("\"");
                    pw.printf("%08X", e.getKey());
                    pw.print("\": ");
                    pw.print(obj.getAsJsonObject().get("curve"));
                    pw.println(",");
                }
                
            }
            pw.println("}");
        }
    }
    
    static void loadBa2(Map<String, Ba2FileEntry> curveMap, String ba2FileName) throws IOException {
        Ba2File baf = new Ba2File();
        File curveFiles = new File(ba2FileName);

        try (RandomAccessFile raf = new RandomAccessFile(curveFiles, "r")) {
            baf.read(raf, name -> name.endsWith("json"));
            
            for (Ba2FileEntry e : baf.entries) {
                curveMap.put(e.name.toLowerCase().replace('\\', '/'), e);
            }
        }
    }
    
    static String readChars(DataInput din, int count) throws IOException {
        char[] chars = new char[count];
        for (int i = 0; i < count; i++) {
            chars[i] = (char)din.readUnsignedByte();
        }
        return new String(chars);
    }
    
    static String intToChar(int v) {
        return "" + ((char)((v >> 0) & 0xFF))
                + ((char)((v >> 8) & 0xFF))
                + ((char)((v >> 16) & 0xFF))
                + ((char)((v >> 24) & 0xFF))
                ;
    }
    
    static int FLAGS_COMPRESSED = 0x00040000;
    
    static void processTopGroups(DataInput din, String filterGroup) throws Exception {
        System.out.println(":---");
        String type = readChars(din, 4);
        System.out.printf("Type: %s%n", type);
        int size = Integer.reverseBytes(din.readInt());
        System.out.printf("Size: %s%n", size);
        
        processInnerGroup(din, filterGroup, type, size);
    }

    static void processInnerGroup(DataInput din, String filterGroup, String type, int size) throws Exception {
        if ("GRUP".equals(type)) {
            int labelOf = Integer.reverseBytes(din.readInt());
            int gtype = Integer.reverseBytes(din.readInt());

            String groupLabel = "";
            if (gtype == 0) {
                groupLabel = intToChar(labelOf);
                System.out.printf("GroupType: Top%n", gtype);
                System.out.printf("Record type: %s%n", groupLabel);
            } else {
                System.out.printf("Label: %08X%n", labelOf);
                System.out.printf("GroupType: %08X%n", gtype);
            }
            // skip version control and unknown
            din.skipBytes(8);

            // data starts here

            if (groupLabel.equals("CELL") || groupLabel.equals("WRLD")) {
                din.skipBytes(size - 24);
            } else {
                if (filterGroup == null || filterGroup.contains(groupLabel)) {
                    try (PrintWriter save = new PrintWriter(new FileWriter(
                            "e:\\Games\\Fallout76\\Data\\Dump\\SeventySix_" + groupLabel + ".txt"))) {
                    
                        int offset = 0;
                        while (offset < size - 24) {
                            offset += processRecords(din, save, filterGroup);
                        }
                    }
                } else {
                    int offset = 0;
                    while (offset < size - 24) {
                        offset += processRecords(din, null, filterGroup);
                    }
                }
            }
        } else {
            int flags = Integer.reverseBytes(din.readInt());
            System.out.printf("Flags: %08X%n", flags);
            if ((flags & FLAGS_COMPRESSED) != 0) {
                System.out.println("       Compressed");
            }
            System.out.printf("ID: %08X%n", Integer.reverseBytes(din.readInt()));
            // skip version control and unknown
            din.skipBytes(8);
            // data starts here
            din.skipBytes(size);
        }
    }
    
    static int processRecords(DataInput din, PrintWriter save, String filterGroup) throws Exception {
        String type = readChars(din, 4);
        int size = Integer.reverseBytes(din.readInt());

        if (type.equals("GRUP")) {
            processInnerGroup(din, filterGroup, type, size);
            return size - 24;
        }
        
        int flags = Integer.reverseBytes(din.readInt());
        boolean isCompressed = (flags & FLAGS_COMPRESSED) != 0;
        int id = Integer.reverseBytes(din.readInt());
        /*
        System.out.println("   ---");
        System.out.printf("   Type: %s%n", type);
        System.out.printf("   Size: %s%n", size);
        System.out.printf("   Flags: %08X%n", flags);
        if (isCompressed) {
            System.out.println("       Compressed");
        }
        System.out.printf("   ID: %08X%n", id);
        */
        
        // skip version control and unknown
        din.skipBytes(8);
        
        if (save != null) {
            save.printf("%s %08X %d%n", type, id, flags);
        }

        if (type.equals("LVLI")) {
            usedFormIDs.add(id);
        }
        
        DataInput fieldInput = din;
        if (isCompressed) {
            int decompressSize = Integer.reverseBytes(din.readInt());
            if (size < 0) {
                System.err.println("wtf? " + ((RandomAccessFile)din).getFilePointer());
            }
            byte[] inputbuf = new byte[size - 4];
            din.readFully(inputbuf);
            
            Inflater inflater = new Inflater();   
            inflater.setInput(inputbuf); 
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(decompressSize);
            byte[] buffer = new byte[1024];  
            while (!inflater.finished()) {  
             int count = inflater.inflate(buffer);  
             outputStream.write(buffer, 0, count);  
            }  
            outputStream.close();  
            byte[] output = outputStream.toByteArray();
            
            fieldInput = new DataInputStream(new ByteArrayInputStream(output));
            
            // data starts here
            //din.skipBytes(size);
        }

        List<FieldEntry> fieldList = processFields(fieldInput, size);

        for (FieldEntry fe : fieldList) {
            //System.out.printf("      + %s%n", fe.asString(type));
            if (save != null) {
                fe.printBinary(save, type);
            }
            if (fe.type.equals("EDID")) {
                saveIds.printf("%s,%08X,%s%n", type, id, fe.getZString());
                edidMap.put(id, type + fe.getZString());
            }
            
            if (type.equals("LVLI")) {
                if (OBJECT_FIELDS.contains(fe.type)) {
                    usedFormIDs.add(fe.getAsObjectID());
                }
                if (fe.type.equals("CTDA")) {
                    usedFormIDs.addAll(fe.getConditionObjectIDs());
                }
            }
            if (type.equals("GLOB") && fe.type.equals("FLTV")) {
                globalValues.put(id, fe.getAsFloat());
            }
            if (type.equals("CURV")) {
                if (fe.type.equals("JASF")) {
                    curveTables.put(id, fe.getZString());
                }
            }
        }
        
        if (type.equals("LVLI")) {
            leveledList.printf("\"%08X\": {%n", id);
            
            int listcount = 0;
            boolean conditionMode = false;
            boolean once = false;
            boolean hasEntl = false;
            boolean hadList = false;
            
            for (int i = 0; i < fieldList.size(); i++) {
                FieldEntry fe = fieldList.get(i);

                if (listcount == 0 && conditionMode && !"CTDA".equals(fe.type)) {
                    conditionMode = false;
                    leveledList.printf("    ],%n");
                }

                if ("LLCT".equals(fe.type)) {
                    listcount = fe.data[0];
                    leveledList.printf("  \"Entries\": [%n");
                    hadList = listcount != 0;
                    continue;
                }
                if (listcount != 0) {
                    if (conditionMode && !"CTDA".equals(fe.type)) {
                        conditionMode = false;
                        leveledList.printf("      ],%n");
                    }
                    switch (fe.type) {
                        case "LVLO":
                            if (once) {
                                leveledList.printf("    },%n");
                            }
                            once = true;
                            leveledList.printf("    {%n");
                            leveledList.printf("      \"Object\": \"%08X\",%n", fe.getAsObjectID());
                            break;
                        case "LVOV": { // omission chance value
                            float fv = fe.getAsFloat();
                            if (fv > 0.0f) {
                                leveledList.printf(Locale.US, "      \"%s\": %f,%n", fe.type, fv);
                            }
                            break;
                        }
                        case "LVIV": { // quantity
                            float fv = fe.getAsFloat();
                            if (fv > 1.0f) {
                                leveledList.printf(Locale.US, "      \"%s\": %f,%n", fe.type, fv);
                            }
                            break;
                        }
                        case "LVLV": { // min level, 0-1 has no relevant meaning here
                            float fv = fe.getAsFloat();
                            if (fv > 1.0f) {
                                leveledList.printf(Locale.US, "      \"%s\": %f,%n", fe.type, fv);
                            }
                            break;
                        }
                        case "LVOC":
                        case "LVOT":
                        case "LVIG":
                        case "LVOG":
                        case "LVLT":
                            leveledList.printf(Locale.US, "      \"%s\": \"%08X\",%n", fe.type, fe.getAsObjectID());
                            break;
                    }
                    if ("CTDA".equals(fe.type)) {
                        if (!conditionMode) {
                            conditionMode = true;
                            leveledList.printf("      \"Conditions\": [%n");
                        }
                        addCTDA("      ", fe);
                    }
                } else {
                    if (conditionMode && !"CTDA".equals(fe.type)) {
                        conditionMode = false;
                        leveledList.printf("  ],%n");
                    }

                    switch (fe.type) {
                        case "LVMG":
                        case "LVMT":
                        case "LVLG":
                        case "LVCT":
                            leveledList.printf("  \"%s\": \"%08X\",%n", fe.type, fe.getAsObjectID());
                            break;
                        case "LVMV":
                        case "LVCV":
                            float fv = fe.getAsFloat();
                        
                            // don't add default-zero entries
                            if (fv != 0.0f) {
                                leveledList.printf(Locale.US, "  \"%s\": %f,%n", fe.type, fv);
                            }
                            break;
                        case "LVLF": {
                            int f = 0;
                            if (fe.data.length >= 1) {
                                f = fe.data[0];
                            }
                            if (fe.data.length >= 2) {
                                f += (fe.data[1] & 0xFF) * 256;
                            }
                            leveledList.print(String.format("  \"%s\": %d,%n", fe.type, f));
                            break;
                        }
                    }
                    if ("CTDA".equals(fe.type)) {
                        if (!conditionMode) {
                            conditionMode = true;
                            leveledList.printf("  \"Conditions\": [%n");
                        }
                        addCTDA("", fe);
                    }
                }
                if ("ENLT".equals(fe.type)) {
                    if (conditionMode) {
                        conditionMode = false;
                        leveledList.printf("      ],%n");
                    }
                    listcount = 0;
                    if (hadList) {
                        leveledList.printf("    },%n");
                        leveledList.printf("  ],%n");
                    }
                    hasEntl = true;
                    break;
                }
            }
            
            if (!hasEntl) {
                if (hadList) {
                    if (conditionMode) {
                        conditionMode = false;
                        leveledList.printf("      ],%n");
                    }
                    leveledList.printf("    },%n");
                    leveledList.printf("  ],%n");
                }
                if (conditionMode) {
                    conditionMode = false;
                    leveledList.printf("  ],%n");
                }
            }
            
            leveledList.printf("},%n");
        }

        return size + 24;
    }
    
    static void addCTDA(String prefix, FieldEntry fe) {

        leveledList.printf("%s    {%n", prefix);
        leveledList.printf("%s      \"Operator\": %d,%n", prefix, fe.data[0]);
        int v = toInt(fe.data[4], fe.data[5], fe.data[6], fe.data[7]);
        if ((fe.data[0] & 4) != 0) {
            leveledList.printf("%s      \"Ref\": \"%08X\",%n", prefix, v);
        } else {
            leveledList.printf(Locale.US, "%s      \"Value\": %s,%n", prefix, Float.intBitsToFloat(v));
        }
        int findex = toInt(fe.data[8], fe.data[9]) + 4096;
        leveledList.printf("%s      \"Function\": %d,%n", prefix, findex);
        leveledList.printf("%s      \"FunctionName\": \"%s\",%n", prefix, FUNCTION_MAP.get(findex));

        int p1 = toInt(fe.data[12], fe.data[13], fe.data[14], fe.data[15]);
        leveledList.printf("%s      \"Param1Ref\": \"%08X\",%n", prefix, p1);
        leveledList.printf(Locale.US, "%s      \"Param1Value\": %s,%n", prefix, Float.intBitsToFloat(p1));
        
        int p2 = toInt(fe.data[16], fe.data[17], fe.data[18], fe.data[19]);
        leveledList.printf("%s      \"Param2Ref\": \"%08X\",%n", prefix, p2);
        leveledList.printf(Locale.US, "%s      \"Param2Value\": %s,%n", prefix, Float.intBitsToFloat(p2));

        switch (fe.data[20]) {
        case 0: {
            leveledList.printf("%s      \"RunOn\": \"Subject\"%n", prefix);
            break;
        }
        case 1: {
            leveledList.printf("%s      \"RunOn\": \"Target\"%n", prefix);
            break;
        }
        case 2: {
            leveledList.printf("%s      \"RunOn\": \"Ref\",%n", prefix);
            int rf = toInt(fe.data[24], fe.data[25], fe.data[26], fe.data[27]);
            leveledList.printf("%s      \"RunOnRef\": \"%08X\"%n", prefix, rf);
            break;
        }
        }
        
        leveledList.printf("%s    },%n", prefix);
    }
    
    static void findEntry(List<FieldEntry> list, String entry, PrintWriter out, BiConsumer<FieldEntry, PrintWriter> handler) {
        for (FieldEntry fe : list) {
            if (entry.equals(fe.type)) {
                handler.accept(fe, out);
            }
        }
    }
    
    static List<FieldEntry> processFields(DataInput din, int size) throws IOException {
        int offset = 0;
        List<FieldEntry> result = new ArrayList<>();
        while (offset < size) {
            String ftype = readChars(din, 4);
            int fsize = din.readUnsignedByte() + din.readUnsignedByte() * 256;
            
            if (fsize == 0) {
                if (result.size() > 0) {
                    FieldEntry last = result.get(result.size() - 1);
                    if (last.type.equals("XXXX")) {
                        fsize = toInt(last.data[0], last.data[1], last.data[2], last.data[3]);
                    }
                }
            }
            
            byte[] data = new byte[fsize];
            din.readFully(data);
            offset += 6 + fsize;
            result.add(new FieldEntry(ftype, data));
        }
        return result;
    }
    
    static final class FieldEntry {
        final String type;
        final byte[] data;
        FieldEntry(String type, byte[] data) {
            this.type = type;
            this.data = data;
        }
        
        @Override
        public java.lang.String toString() {
            StringBuilder sb = new StringBuilder();
            switch (type) {
            case "EDID":
            case "CNAM":
            case "DNAM":
            case "SNAM":
                sb.append(getZString()); 
                break;
            case "LLCT": {
                sb.append(data[0]);
                break;
            }
            case "LVLO": {
                sb.append(String.format("%08X (object)", getAsObjectID()));
                break;
            }
            case "LVOG": {
                sb.append(String.format("%08X (global)", getAsObjectID()));
                break;
            }
            case "LVOC": {
                sb.append(String.format("%08X (global)", getAsObjectID()));
                break;
            }
            // epic chance global
            case "LVSG": {
                sb.append(String.format("%08X (global)", getAsObjectID()));
                break;
            }
            // chance none global
            case "LVLG": {
                sb.append(String.format("%08X (global)", getAsObjectID()));
                break;
            }
            case "LVOT": {
                sb.append(String.format("%08X (curve)", getAsObjectID()));
                break;
            }
            // chance none value
            case "LVCV":
            case "LVOV":
            case "LVMV":
            case "LVIV":
            case "LVLV":
            case "FLTV":
                sb.append(String.format("%.5f", getAsFloat()));
                break;
            case "LVLF": {
                int f = 0;
                if (data.length >= 1) {
                    f = data[0];
                }
                if (data.length >= 2) {
                    f += (data[1] & 0xFF) * 256;
                }
                sb.append(String.format("%04X", f));
                if ((f & 1) != 0) {
                    sb.append(String.format(" +level"));
                }
                if ((f & 2) != 0) {
                    sb.append(String.format(" +each"));
                }
                if ((f & 4) != 0) {
                    sb.append(String.format(" +all"));
                }
                if ((f & 8) != 0) {
                    sb.append(String.format(" +u3"));
                }
                if ((f & 16) != 0) {
                    sb.append(String.format(" +refspawn"));
                }
                if ((f & 32) != 0) {
                    sb.append(String.format(" +u5"));
                }
                if ((f & 64) != 0) {
                    sb.append(String.format(" +first-match-all-cond"));
                }
                if ((f & 128) != 0) {
                    sb.append(String.format(" +u7"));
                }
                break;
            }
            /*
            case "CTDA": {
                printConditionData(sb);
                break;
            }
            */
            default: {
                for (byte b : data) {
                    sb.append(String.format("%02X", b));
                }
            }
            }
            return sb.toString();
        }
        
        String asString(String parentType) {
            String result = "";
            switch (type) {
            case "EDID": {
                result = "EDID: " + getZString();
                break;
            }
            case "CNAM": {
                if (!parentType.equals("KYWD")) {
                    result = "CNAM: " + getZString();
                }
                break;
            }
            case "DNAM": {
                result = "DNAM: " + getZString();
                break;
            }
            case "SNAM": {
                result = "SNAM: " + getZString();
                break;
            }
            default: 
                result = type + " (" + data.length + ")";
            }
            return result;
        }
        
        void printBinary(PrintWriter out, String parentType) {
            out.printf("  %s (%d): ", type, data.length);
            
            switch (type) {
                case "EDID": {
                    out.print(getZString());
                    break;
                }
                case "LLCT": {
                    out.print(data[0]);
                    break;
                }
                case "LVLO": {
                    out.printf("%08X (object)", getAsObjectID());
                    break;
                }
                case "LVOG": {
                    out.printf("%08X (global)", getAsObjectID());
                    break;
                }
                case "LVOC": {
                    out.printf("%08X (global)", getAsObjectID());
                    break;
                }
                // epic chance global
                case "LVSG": {
                    out.printf("%08X (global)", getAsObjectID());
                    break;
                }
                // chance none global
                case "LVLG": {
                    out.printf("%08X (global)", getAsObjectID());
                    break;
                }
                case "LVOT": {
                    out.printf("%08X (curve)", getAsObjectID());
                    break;
                }
                // chance none value
                case "LVCV": {
                    out.printf("%.5f", getAsFloat());
                    break;
                }
                case "LVOV": {
                    out.printf("%.5f", getAsFloat());
                    break;
                }
                case "LVMV": {
                    out.printf("%.5f", getAsFloat());
                    break;
                }
                case "LVIV": {
                    out.printf("%.5f", getAsFloat());
                    break;
                }
                case "LVLV": {
                    out.printf("%.5f", getAsFloat());
                    break;
                }
                case "FLTV": {
                    out.printf("%.5f", getAsFloat());
                    break;
                }
                case "LVLF": {
                    int f = 0;
                    if (data.length >= 1) {
                        f = data[0];
                    }
                    if (data.length >= 2) {
                        f += (data[1] & 0xFF) * 256;
                    }
                    out.printf("%04X", f);
                    if ((f & 1) != 0) {
                        out.printf(" +level");
                    }
                    if ((f & 2) != 0) {
                        out.printf(" +each");
                    }
                    if ((f & 4) != 0) {
                        out.printf(" +all");
                    }
                    if ((f & 8) != 0) {
                        out.printf(" +u3");
                    }
                    if ((f & 16) != 0) {
                        out.printf(" +refspawn");
                    }
                    if ((f & 32) != 0) {
                        out.printf(" +u5");
                    }
                    if ((f & 64) != 0) {
                        out.printf(" +first-match-all-cond");
                    }
                    if ((f & 128) != 0) {
                        out.printf(" +u7");
                    }
                    break;
                }
                case "CTDA": {
                    printConditionData(out);
                    break;
                }
                default: {
                    for (byte b : data) {
                        out.printf("%02X", b);
                    }
                }
            }
            out.println();
        }

        float getAsFloat() {
            return Float.intBitsToFloat(getAsObjectID());
        }

        int getAsObjectID() {
            return toInt(data[0], data[1], data[2], data[3]);
        }
        
        List<Integer> getConditionObjectIDs() {
            List<Integer> result = new ArrayList<>();

            // global flag
            if ((data[0] & 4) != 0) {
                result.add(toInt(data[4], data[5], data[6], data[7]));
            }
            // param 1
            result.add(toInt(data[12], data[13], data[14], data[15]));
            // param 2
            result.add(toInt(data[16], data[17], data[18], data[19]));
            
            // run on: reference
            if (data[20] == 2) {
                result.add(toInt(data[24], data[25], data[26], data[27]));
            }
            
            return result;
        }

        void printConditionData(PrintWriter out) {
            out.println();
            out.printf("    Operator:");
            int optype = (data[0] & 0xFF) >> 5;
            switch (optype) {
            case 0:
                out.printf(" ==");
                break;
            case 1:
                out.printf(" !=");
                break;
            case 2:
                out.printf(" >");
                break;
            case 3:
                out.printf(" >=");
                break;
            case 4:
                out.printf(" <");
                break;
            case 5:
                out.printf(" <=");
                break;
            default:
                out.printf(" %d ???", optype);
            }
            if ((data[0] & 1) == 0) {
                out.printf(" AND");
            } else {
                out.printf(" OR");
            }
            if ((data[0] & 2) != 0) {
                out.printf(" Parameters");
            }
            if ((data[0] & 4) != 0) {
                out.printf(" Global");
            }
            out.println();
            int val = toInt(data[4], data[5], data[6], data[7]);
            if ((data[0] & 4) != 0) {
                out.printf("    Global value: %08X%n", val);
            } else {
                out.printf("    Float value: %.5f%n", Float.intBitsToFloat(val));
            }
            int findex = toInt(data[8], data[9]) + 4096;
            if (!FUNCTION_MAP.containsKey(findex)) {
                System.err.println("Unknown function: " + findex + " (" + (findex - 4096) + ")");
                FUNCTION_MAP.put(findex, "Unknown");
            }
            out.printf("    Function: %d (%s)%n", findex, FUNCTION_MAP.get(findex));
            out.printf("    Param1: %08X%n", toInt(data[12], data[13], data[14], data[15]));
            out.printf("    Param2: %08X%n", toInt(data[16], data[17], data[18], data[19]));
            
            out.print("    RunOn: ");
            switch (data[20]) {
                case 0: {
                    out.print("Subject");
                    break;
                }
                case 1: {
                    out.print("Target");
                    break;
                }
                case 2: {
                    out.printf("Reference %08X", toInt(data[24], data[25], data[26], data[27]));
                    break;
                }
                default:
                    out.printf("%08X", toInt(data[20], data[21], data[22], data[23]));
            }
        }

        String getZString() {
            return new String(data, 0, data.length - 1, StandardCharsets.ISO_8859_1);
        }
    }

    static int toInt(byte b1, byte b2, byte b3, byte b4) {
        return (b1 & 0xFF) + ((b2 & 0xFF) << 8)
                + ((b3 & 0xFF) << 16) + ((b4 & 0xFF) << 24);
    }
    
    static int toInt(byte b1, byte b2) {
        return (b1 & 0xFF) + ((b2 & 0xFF) << 8);
    }

    static final Map<Integer, String> FUNCTION_MAP = new HashMap<>();
    static {
        FUNCTION_MAP.put(4778, "WornHasKeyword");
        FUNCTION_MAP.put(4173, "GetRandomPercent");
        FUNCTION_MAP.put(4170, "GetGlobalValue");
        FUNCTION_MAP.put(4639, "GetQuestCompleted");
        FUNCTION_MAP.put(4675, "EditorLocationHasKeyword");
        
        FUNCTION_MAP.put(4544, "HasPerk");
        FUNCTION_MAP.put(4165, "GetIsRace");
        FUNCTION_MAP.put(4176, "GetLevel");
        FUNCTION_MAP.put(4154, "GetStage");
        FUNCTION_MAP.put(4659, "LocationHasRefType");

        FUNCTION_MAP.put(4110, "GetActorValue");
        FUNCTION_MAP.put(4143, "GetItemCount");
        FUNCTION_MAP.put(4656, "HasKeyword");
        FUNCTION_MAP.put(4101, "GetLocked");
        FUNCTION_MAP.put(4161, "GetLockLevel");

        FUNCTION_MAP.put(4168, "GetIsID");
        FUNCTION_MAP.put(4455, "GetInCurrentLoc");
        FUNCTION_MAP.put(4657, "HasRefType");
        FUNCTION_MAP.put(4097, "GetDistance");
        FUNCTION_MAP.put(4278, "GetEquipped");

        FUNCTION_MAP.put(4661, "GetIsEditorLocation");
        FUNCTION_MAP.put(4266, "GetDayOfWeek");
        FUNCTION_MAP.put(4955, "HasEntitlement");
        FUNCTION_MAP.put(4949, "HasLearnedRecipe");
        FUNCTION_MAP.put(4971, "IsTrueForConditionForm");

        FUNCTION_MAP.put(4945, "GetIsInRegion");
        FUNCTION_MAP.put(4953, "GetNumTimesCompletedQuest");
        FUNCTION_MAP.put(4933, "IsActivePlayer");
        FUNCTION_MAP.put(4994, "GetWorldType");
        FUNCTION_MAP.put(4942, "GetActorValueForCurrentLocation");

        FUNCTION_MAP.put(4929, "GetStageDoneUniqueQuest");
        FUNCTION_MAP.put(4950, "HasActiveMagicEffect");
        FUNCTION_MAP.put(4884, "LocationHasPlayerOwnedWorkshop");
    }
    
    static final Set<String> OBJECT_FIELDS = new HashSet<>(Arrays.asList(
            "LVLO", // object ref
            "LVOG", // minimum level global ref
            "LVOC", // omission global ref 
            "LVOT", // omission curve table
            "LVIG", // quantity global

            "LVSG", // epic chance global ref

            "LVLG", // list omission global
            "LVCT",  // list omission curve table
            "LVLT", // list minimum level global ref
            "LVMG", // list max global
            "LVMT" // list max curve table
    ));
    
    static final Set<String> IGNORE_FIELDS = new HashSet<>(Arrays.asList(
            "OBND", "ONAM", "ENLT", "ENLS", "AUUV"
    ));
}
