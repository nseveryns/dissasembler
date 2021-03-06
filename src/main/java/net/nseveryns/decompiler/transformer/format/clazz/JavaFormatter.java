package net.nseveryns.decompiler.transformer.format.clazz;

import org.apache.commons.io.FilenameUtils;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Format the data from class formatter. This will format it into familiar java code.
 *
 * @author nseveryns
 */
public class JavaFormatter {
    private static final String SPACES = "    ";
    private final int accessBitmask;
    private final int identity;
    private final int superIdentity;
    private final ConstantPoolTable constants;
    private final int[] interfaces;
    private final FieldTable fields;
    private final MethodTable methods;
    private final Map<Integer, InstructionSet> codes;
    private final StringBuilder builder;
    private final Set<String> imports;
    private int packageEndIndex;

    public JavaFormatter(int access, int identity, int superIdentity, ConstantPoolTable constants, int[] interfaces,
                         FieldTable fields, MethodTable methods, Map<Integer, InstructionSet> codes) {
        this.accessBitmask = access;
        this.identity = identity;
        this.superIdentity = superIdentity;
        this.constants = constants;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.codes = codes;
        this.builder = new StringBuilder();
        this.imports = new HashSet<>();
        this.addHeader();
        this.addFields();
        this.addMethods();
        this.finish();
    }

    private void addImport(String importPath) {
        if (this.imports.add(importPath)) {
            this.builder.insert(packageEndIndex, "\nimport " + importPath.replace("/", ".") + ";");
        }
    }

    private void addHeader() {
        boolean classType = true;
        builder.append("\n\n");
        for (ClassAccessFlags flags : ClassAccessFlags.values()) {
            if ((accessBitmask & flags.flag) == flags.flag) {
                if (flags == ClassAccessFlags.SYNTHETIC) {
                    return;
                }
                builder.append(flags.name().toLowerCase()).append(" ");
                if (flags == ClassAccessFlags.ENUM || flags == ClassAccessFlags.INTERFACE) {
                    classType = false;
                }
            }
        }
        if (classType) {
            builder.append("class ");
        }
        String classPath = readString(constants.getEntry(getShort(constants.getEntry(identity))));
        String path = FilenameUtils.getPath(classPath);
        this.packageEndIndex = path.length() + 9;
        builder.insert(0, "package " + path.replace("/", ".").substring(0, path.length() - 1) + ";" + "\n");
        builder.append(FilenameUtils.getName(classPath)).append(" ");
        if (superIdentity != 0) {
            short superIndex = getShort(constants.getEntry(superIdentity));
            String superName = readString(constants.getEntry(superIndex));
            String name = FilenameUtils.getName(superName);
            if (!name.equals("Object") && !name.equals("Enum")) { //This is already default
                addImport(superName);
                builder.append("extends ").append(name).append(" ");
            }
        }
        if (interfaces.length > 0) {
            builder.append("implements ");
            for (int interfaceIndex : interfaces) {
                short index = getShort(this.constants.getEntry(interfaceIndex));
                String interfacePath = readString(this.constants.getEntry(index));
                addImport(interfacePath);
                builder.append(FilenameUtils.getName(interfacePath));
            }
        }
        builder.append(" {\n");
    }

    private void addMethods() {
        for (MethodTable.Method method : this.methods.getMethods()) {
            addMethod(method);
        }
    }

    private void addMethod(MethodTable.Method method) {
        builder.append("\n");
        builder.append(SPACES);
        for (MethodAccessFlags flags : MethodAccessFlags.values()) {
            if ((method.getFlags() & flags.flag) == flags.flag) {
                if (flags == MethodAccessFlags.SYNTHETIC) {
                    return;
                }
                builder.append(flags.name().toLowerCase()).append(" ");
            }
        }
        String methodName = readString(constants.getEntry(method.getNameIndex()));
        if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
            builder.append(FilenameUtils.getName(readString(constants.getEntry(getShort(constants.getEntry(identity))))));
            builder.append("() {").append("\n\n").append(SPACES).append("}").append("\n");
            return;
        }
        builder.append(methodName).append("() {").append("\n");
        for (Attribute attribute : method.getAttributes()) {
            String name = readString(constants.getEntry(attribute.getAttributeNameIndex()));
            switch (name) {
                case "Code":
                    CodeAttribute code = new CodeAttribute(attribute);
                    builder.append(formatBytecode(code.getCode()));
                    //for (Attribute subAttrib : code.getAttributes()) {
                    //     //TODO: ADD SUB ATTRIBUTES.
                    //}
            }
        }
        builder.append("\n").append(SPACES).append("}").append("\n");
    }

    private String formatBytecode(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0, bytesLength = bytes.length; i < bytesLength; i++) {
            byte b = bytes[i];
            int unsignedInt = Byte.toUnsignedInt(b);
            InstructionSet s = codes.get(unsignedInt);
            if (s == null) {
                System.out.printf("0x%02x \n", unsignedInt);
                continue;
            }
            builder.append(SPACES).append(SPACES).append(s.getName()).append(" ");
            int extra = s.getExtra();
            if (extra > 0) {
                byte[] extraCode = new byte[extra];
                int value = new BigInteger(extraCode).intValue();
                int index;
                if (extraCode.length == 2) {
                    index = (extraCode[0] << 8) | extraCode[1];
                } else {
                    index = value;
                }
                ConstantPoolTable.Entry entry = constants.getEntry(index);
                if (entry.getType() == ConstantPoolTable.ConstantTagType.FIELD_REFERENCE
                        || entry.getType() == ConstantPoolTable.ConstantTagType.METHOD_REFERENCE
                        || entry.getType() == ConstantPoolTable.ConstantTagType.INTERFACE_METHOD_REFERENCE) {
                    int firstIndex = getShort(entry);
                    ConstantPoolTable.Entry classInfo = constants.getEntry(firstIndex);
                    String name = readString(constants.getEntry(getShort(classInfo)));

                    byte[] entryBytes = entry.getBytes();
                    int secondIndex = (entryBytes[2] << 8) | entryBytes[3];
                    entry = constants.getEntry(secondIndex);
                   // System.out.println(entry.getType().name() + " - " + entry.getBytes().length);
                    String methodCall = readString(constants.getEntry(getShort(entry)));
//                    String name = readString(constants.getEntry(getShort(entry.getBytes()[2], entry.getBytes()[3])));
                    builder.append("#").append(getShort(entry)).append(SPACES).append("//").append(name).append(methodCall);
                }
            }
            builder.append("\n");
            i+= extra;
        }
        return builder.toString();
    }

    private void addFields() {
        for (FieldTable.Field field : this.fields.getFields()) {
            this.addField(field);
        }
    }

    private void addField(FieldTable.Field field) {
        builder.append(SPACES);
        for (FieldAccessFlags flags : FieldAccessFlags.values()) {
            if ((field.getFlags() & flags.flag) == flags.flag) {
                if (flags == FieldAccessFlags.SYNTHETIC) {
                    return;
                }
                builder.append(flags.name().toLowerCase()).append(" ");
            }
        }
        String type = readString(constants.getEntry(field.getDescriptorIndex()));
        String substring = type.substring(1, type.length() - 1);
        String className = FilenameUtils.getName(substring);
        addImport(FilenameUtils.getPath(substring) + className);
        builder.append(className).append(" ");
        builder.append(readString(constants.getEntry(field.getNameIndex()))).append(";").append("\n");
    }

    private void finish() {
        builder.append("}");
    }

    public String format() {
        return builder.toString();
    }


    private short getShort(ConstantPoolTable.Entry entry) {
        byte[] bytes = entry.getBytes();
        return getShort(bytes[0], bytes[1]);
    }

    private short getShort(byte high, byte low) {
        return (short) ((high & 0xFF) << 8 | (low & 0xFF));
    }

    private String readString(ConstantPoolTable.Entry entry) {
        return readString(entry.getBytes());
    }

    private String readString(byte[] bytes) {
        return new String(bytes);
    }

    private enum ClassAccessFlags {
        SYNTHETIC(0x1000),
        PUBLIC(0x0001),
        FINAL(0x0010),
        SUPER(0x0020),
        ABSTRACT(0x0400),
        INTERFACE(0x0200),
        ANNOTATION(0x2000),
        ENUM(0x4000);

        private final int flag;

        ClassAccessFlags(int flag) {

            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    private enum FieldAccessFlags {
        SYNTHETIC(0x1000),
        PUBLIC(0x0001),
        PRIVATE(0x0002),
        PROTECTED(0x0004),
        STATIC(0x0008),
        FINAL(0x0010),
        VOLATILE(0x0040),
        TRANSIENT(0x0080),
        ENUM(0x4000);

        private final int flag;

        FieldAccessFlags(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }


    private enum MethodAccessFlags {
        SYNTHETIC(0x1000),
        PUBLIC(0x0001),
        PRIVATE(0x0002),
        PROTECTED(0x0004),
        STATIC(0x0008),
        FINAL(0x0010),
        SYNCHRONIZED(0x0020),
        BRIDGE(0x0040),
        VARARGS(0x0080),
        NATIVE(0x0100),
        ABSTRACT(0x0400),
        STRICTFP(0x0800);

        private final int flag;

        MethodAccessFlags(int flag) {
            this.flag = flag;
        }

        public int getFlag() {
            return flag;
        }
    }

    private enum Attributes {
        CODE;

    }
}
