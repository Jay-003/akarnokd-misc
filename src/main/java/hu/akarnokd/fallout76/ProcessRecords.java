package hu.akarnokd.fallout76;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

public class ProcessRecords {
    static int processRecords(DataInput din, PrintWriter save, String filterGroup, String debugPrefix) throws Exception {
        String type = EsmExport.readChars(din, 4);
        int size = Integer.reverseBytes(din.readInt());


        if (type.equals("GRUP")) {
            EsmExportHelper.processInnerGroup(din, filterGroup, type, size, debugPrefix + "  ");
            return size;
        }


        int flags = Integer.reverseBytes(din.readInt());
        boolean isCompressed = (flags & EsmExport.FLAGS_COMPRESSED) != 0;
        int id = Integer.reverseBytes(din.readInt());


       /*
//        if (type.equals("WRLD") || type.equals("CELL"))
       {
           System.out.printf("%s%s record (size: %d): %08X%s%n", debugPrefix, type, size, id, isCompressed ? "  compressed" : "");
       }
       */
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
            EsmExport.usedFormIDs.add(id);
        }


        int propertySize = size;
        DataInput fieldInput = din;
        if (isCompressed) {
            byte[] output = EsmExport.getBytes(din, size);

            fieldInput = new DataInputStream(new ByteArrayInputStream(output));

            // data starts here
            //din.skipBytes(size);
            propertySize = output.length;
        }


        List<EsmExport.FieldEntry> fieldList = EsmExport.processFields(fieldInput, propertySize);


        EsmExport.processAndExtractFieldEntries(save, type, id, fieldList);


        if (type.equals("LVLI")) {
            EsmExport.leveledList.printf("\"%08X\": {%n", id);


            int listcount = 0;
            boolean conditionMode = false;
            boolean once = false;
            boolean hasEntl = false;
            boolean hadList = false;


            for (int i = 0; i < fieldList.size(); i++) {
                EsmExport.FieldEntry fe = fieldList.get(i);


                if (listcount == 0 && conditionMode && !"CTDA".equals(fe.type)) {
                    conditionMode = false;
                    EsmExport.leveledList.printf("    ],%n");
                }

                if ("LLCT".equals(fe.type)) {
                    listcount = fe.data[0];
                    EsmExport.leveledList.printf("  \"Entries\": [%n");
                    hadList = listcount != 0;
                    continue;
                }

                if (listcount != 0) {
                    if (conditionMode && !"CTDA".equals(fe.type)) {
                        conditionMode = false;
                        EsmExport.leveledList.printf("      ],%n");
                    }
                    switch (fe.type) {
                        case "LVLO":
                            if (once) {
                                EsmExport.leveledList.printf("    },%n");
                            }
                            once = true;
                            EsmExport.leveledList.printf("    {%n");
                            if (fe.data.length == 4) {
                                EsmExport.leveledList.printf("      \"Object\": \"%08X\",%n", fe.getAsObjectID());
                            } else {
                                EsmExport.leveledList.printf("      \"Object\": \"%08X\",%n", fe.getAsObjectID(4));
                                if (fe.data[10] > 0) {
                                    EsmExport.leveledList.printf(Locale.US, "      \"%s\": %d,%n", "LVOV", fe.data[10]);
                                }
                                if (fe.getAsShort(8) > 1) {
                                    EsmExport.leveledList.printf(Locale.US, "      \"%s\": %d,%n", "LVIV", fe.getAsShort(8));
                                }
                                if (fe.getAsShort(0) > 1) {
                                    EsmExport.leveledList.printf(Locale.US, "      \"%s\": %d,%n", "LVLV", fe.getAsShort(0));
                                }
                            }
                            break;
                        case "LVOV": { // omission chance value
                            float fv = fe.getAsFloat();
                            if (fv > 0.0f) {
                                EsmExport.leveledList.printf(Locale.US, "      \"%s\": %f,%n", fe.type, fv);
                            }
                            break;
                        }
                        case "LVIV", "LVLV": { // quantity
                            float fv = fe.getAsFloat();
                            if (fv > 1.0f) {
                                EsmExport.leveledList.printf(Locale.US, "      \"%s\": %f,%n", fe.type, fv);
                            }
                            break;
                        }// min level, 0-1 has no relevant meaning here
                        case "LVOC":
                        case "LVOT":
                        case "LVIG":
                        case "LVOG":
                        case "LVLT":
                            EsmExport.leveledList.printf(Locale.US, "      \"%s\": \"%08X\",%n", fe.type, fe.getAsObjectID());
                            break;
                    }
                    if ("CTDA".equals(fe.type)) {
                        if (!conditionMode) {
                            conditionMode = true;
                            EsmExport.leveledList.printf("      \"Conditions\": [%n");
                        }
                        EsmExport.addCTDA("      ", fe);
                    }
                } else {
                    conditionMode = EsmExport.isConditionMode(conditionMode, fe);
                }
                if ("ENLT".equals(fe.type)) {
                    if (conditionMode) {
                        conditionMode = false;
                        EsmExport.leveledList.printf("      ],%n");
                    }
                    listcount = 0;
                    if (hadList) {
                        EsmExport.leveledList.printf("    },%n");
                        EsmExport.leveledList.printf("  ],%n");
                    }
                    hasEntl = true;
                    break;
                }
            }


            if (!hasEntl) {
                EsmExport.hadList(conditionMode, hadList);
            }

            EsmExport.leveledList.printf("},%n");
        }


        return size + 24;
    }
}
