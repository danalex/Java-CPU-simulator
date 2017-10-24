package cpusimulator.code;

import java.util.Map;

/**
 * This class contains operand type and data. Also there is operand assembling
 * and checking capabilities.
 * 
 * @author Daniel Alexandre 2011
 */
public class Operand {
    
    /** Specify registers count to make asm code correctly executable on our CPU model */
    private static final int REGISTERS = 16;
    /** Specify memory cells count to make asm code correctly executable on our CPU model */
    private static final int MEMCELLS = 256;
    /** Specify code cells count to make asm code correctly executable on our CPU model */
    private static final int CODECELLS = 64;
    
    public static class Type {
        
        /** Operand type : register : R0, R1, R2 */
        public final static Type REGISTER = new Type(4, "register");
        /** Operand type : memory address : [FEh] */
        public final static Type MEMADDR = new Type(8, "address");
        /** Number : just a number: 25, 30h, 0, 1 */
        public final static Type NUMBER4 = new Type(4, "number");
        /** Label : my_l_123, end_code, #FAh */
        public final static Type LABEL = new Type(8, "label");
        
        /** Count of bits in operand's binary representation */
        private int size;
        /** Operand type's name. */
        private String name;
        
        /**
         * Creating a new operand type. Can be called only inside of enum.
         * @param size size of operand's binary representation, in bits
         * @param name operand type's name
         */
        private Type(int size, String name) {
            this.size = size;
            this.name = name;
        }
        
        /**
         * Returns size of operand's representation, int bits.
         * @return operand's bit count
         */
        public int getSize() {
            return size;
        }
        
        /**
         * Returns name of operand type
         * @return operand type's name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Creates new operand from the operand's type.
         * @param value binary data from which operand's data will be retrieved;
         * data will be cutted from the higher part of value's lowest _bits_ bits
         * @param bits operand's data offset. for example, 8 means lowest byte's
         * higher bits (or entire lowest byte if operand have 8 bits of data)
         * @return newly created operand with cutted out operand data.
         */
        public Operand createOperand(int value, int bits) {
            if(bits < size) throw new IllegalArgumentException("too few bits");
            return new Operand(this, (value >>> (bits - size)) & ((1 << size) - 1));
        }
    }
    
    /** Operand's type */
    private Type type;
    /** Data of opperand: register number / memory cell / flag / code addres */
    private int data;
    /** Data of operand, normally contans label's name. */
    private String string;
    
    /**
     * Creates a new operand from specified type and bits.
     * @param type type of operand
     * @param data operand's data
     */
    private Operand(Type type, int data) {
        this.type = type;
        this.data = data;
    }
    
    /**
     * Creates a new operand from specified type and string (usually, type is LABEL).
     * @param type type of operand
     * @param string string data
     */
    private Operand(Type type, String string) {
        this.type = type;
        this.string = string;
    }
    
    /**
     * Replaces label's name by offset. Useful in assembling.
     * @param data label's code address
     * @throws IllegalStateException if label already have no name or if operand isn't a label
     */
    public void replaceLabel(int data) {
        if(type != Type.LABEL || string == null) throw new IllegalStateException("this operation can be done only for labels");
        this.string = null;
        this.data = data;
    }
    
    /**
     * Replaces label's code address by name. Useful in disassembling.
     * @param string label's name.
     * @throws IllegalStateException if label already have a name or if operand isn't a label
     */
    public void replaceLabel(String string) {
        if(type != Type.LABEL || string != null) throw new IllegalStateException("this operation can be done only for labels");
        this.string = string;
        this.data = 0;
    }
    
    /**
     * Returns type of this operand.
     * @return operand's type
     */
    public Type getType() {
        return type;
    }
    
    /**
     * Returns data of this operand. If this operand is label with #ADDR address
     * format, method will parse that format and return label's address.
     * @return this operand's data
     */
    public int getData() {
        if(type == Type.LABEL && string != null && string.startsWith("#")) {
            try {
                return parseInt(string.substring(1));
            } catch(NumberFormatException exception) {
                throw new IllegalStateException("operand with wrong address");
            }
        }
        return data;
    }
    
    /**
     * Returns this operand's string.
     * @return string of ths operand or null if operand have null string
     */
    public String getString() {
        return string;
    }
    
    /**
     * Returns ASM operand's representation
     * @param labels map with labels which is used to replace label addresses by names
     * @return disassembled operand
     */
    public String getAsm(Map<Integer, String> labels) {
        if(type == Type.REGISTER) return "R" + data;
        else if(type == Type.MEMADDR) return "[" + data + "]";
        else if(type == Type.NUMBER4) return String.valueOf(data);
        else if(type == Type.LABEL) {
            String name = labels.get(new Integer(data));
            return name == null ? "#" + String.valueOf(data) : name;
        } else throw new RuntimeException("unknown operand type");
    }
    
    /**
     * Assembles operand from its ASM representation.
     * @param operand operand's ASM representation
     * @return parsed Operand
     * @throws AssembleException if there was errors during operand assembling
     */
    public static Operand parse(String operand) throws AssembleException {
        // first make all letters low case to make it case-insensitive
        operand = operand.toLowerCase();
        if(operand.startsWith("r") || operand.startsWith("R")) {
            // we have register-type operand
            try {
                int data = Integer.parseInt(operand.substring(1));
                if(data < 0 || data >= REGISTERS) throw new AssembleException("We have only " + REGISTERS + " registers numerated from 0 to " + (REGISTERS - 1));
                return new Operand(Type.REGISTER, data);
            } catch(NumberFormatException exception) {
                throw new AssembleException("Wrong register number, it should be a DEC number");
            }
        }
        if(operand.startsWith("[") && operand.endsWith("]")) {
            // we have address-type operand
            try {
                int data = parseInt(operand.substring(1, operand.length() - 1).trim());
                if(data < 0 || data >= MEMCELLS) throw new AssembleException("We have only " + MEMCELLS + " memory cells numerated from 0 to " + (MEMCELLS - 1));
                return new Operand(Type.MEMADDR, data);
            } catch(NumberFormatException exception) {
                throw new AssembleException("Wrong memory address, it should be a HEX/DEC/BIN number");
            }
        }
        if(operand.startsWith("#")) {
            // we have label-type operand
            try {
                int data = parseInt(operand.substring(1, operand.length()).trim());
                if(data < 0 || data >= CODECELLS) throw new AssembleException("We have only " + CODECELLS + " code cells numerated from 0 to " + (CODECELLS - 1));
            } catch(NumberFormatException exception) {
                throw new AssembleException("Wrong label, it should be an HEX/DEC/BIN number");
            }
        }
        try {
            // operand's type isn't ADDRESS or register. trying to parse. successfull number parse means that operand has number type
            return new Operand(Type.NUMBER4, parseInt(operand));
        } catch(NumberFormatException exception) {
            // there was errors in integer number parsing, deciding to create operand of label type
            return new Operand(Type.LABEL, operand);
        }
    }
    
    /**
     * Utility method to allow parse integers with H (hex) and B (bin) suffixes.
     * This allows to use numbers of different radixes in ASM code
     * @param string unparsed number
     * @return parsed number
     */
    private static int parseInt(String string) {
        // firstly, we try to check and parse hexadecimal number
        if(string.endsWith("h") || string.endsWith("H")) return Integer.parseInt(string.substring(0, string.length() - 1), 16);
        // then try to check and parse binary
        if(string.endsWith("b") || string.endsWith("B")) return Integer.parseInt(string.substring(0, string.length() - 1), 2);
        // and decimal if two above lines of code haven't parsed the number
        return Integer.parseInt(string);
    }
}
