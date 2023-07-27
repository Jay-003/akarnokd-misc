package hu.akarnokd.fallout76;

import java.io.*;
import java.util.Map;
public class EsmExportHelper {

    static void loadStringsBa2(Map<String, Ba2FileEntry> curveMap, String ba2FileName) throws IOException {
        Ba2File baf = new Ba2File();
        File curveFiles = new File(ba2FileName);


        try (RandomAccessFile raf = new RandomAccessFile(curveFiles, "r")) {
            baf.read(raf, name -> name.toLowerCase().endsWith("seventysix_en.strings"));


            for (Ba2FileEntry e : baf.entries) {
                curveMap.put(e.name.toLowerCase().replace('\\', '/'), e);
            }
        }
    }

    static void processInnerGroup(DataInput din, String filterGroup, String type, int size, String debugPrefix) throws Exception {
        if ("GRUP".equals(type)) {
            int labelOf = Integer.reverseBytes(din.readInt());
            int gtype = Integer.reverseBytes(din.readInt());


            String groupLabel = "";
            //System.out.printf("%sv-v-v-v-v-v-v-v-v-v%n", debugPrefix);
            //System.out.printf("%sSize: %d%n", debugPrefix, size);
            int logLimit = 6;
            if (gtype == 0) {
                groupLabel = EsmExport.intToChar(labelOf);
               /*
               System.out.printf("%sGroupType: Top%n", debugPrefix, gtype);
               System.out.printf("%sRecord type: %s%n", debugPrefix, groupLabel);
               */
                if (debugPrefix.length() < logLimit) {


                    System.out.printf("%sGRUP (size: %,d) for %s (type: %d) [%.3f%%]%n",
                            debugPrefix, size, groupLabel, gtype, EsmExport.getProgress(din));
                }
            } else {
               /*
               System.out.printf("%sLabel: %08X%n", debugPrefix, labelOf);
               System.out.printf("%sGroupType: %08X%n", debugPrefix, gtype);
               */
                if (debugPrefix.length() < logLimit) {
                    System.out.printf("%sGRUP (size: %,d) for %08X (type: %d) [%.3f%%]%n",
                            debugPrefix, size, labelOf, gtype, EsmExport.getProgress(din));
                }
            }
            // skip version control and unknown
            din.skipBytes(8);


            // data starts here


            if (groupLabel.equals("aaaa")) {
//            if (!groupLabel.equals("WRLD") && debugPrefix.length() == 0) {
//            if (groupLabel.equals("CELL") || groupLabel.equals("WRLD")) {
                din.skipBytes(size - 24);
            } else {
                if (!groupLabel.isEmpty() && (filterGroup == null || filterGroup.contains(groupLabel))) {
                    try (PrintWriter save = new PrintWriter(new FileWriter(
                            EsmExport.basePath + "Dump\\SeventySix_" + groupLabel + ".txt"))) {


                        int offset = 0;
                        while (offset < size - 24) {
                            offset += ProcessRecords.processRecords(din, save, filterGroup, debugPrefix);
                        }
                    }
                } else {
                    int offset = 0;
                    while (offset < size - 24) {
                        offset += ProcessRecords.processRecords(din, null, filterGroup, debugPrefix);
                    }
                }
            }


            //System.out.printf("%s^-^-^-^-^-^-^-^-^-^%n", debugPrefix);
        } else {
            int flags = Integer.reverseBytes(din.readInt());
            System.out.printf("Flags: %08X%n", flags);
            if ((flags & EsmExport.FLAGS_COMPRESSED) != 0) {
                System.out.println("       Compressed");
            }
            System.out.printf("ID: %08X%n", Integer.reverseBytes(din.readInt()));
            // skip version control and unknown
            din.skipBytes(8);
            // data starts here
            din.skipBytes(size);
        }
    }
}
