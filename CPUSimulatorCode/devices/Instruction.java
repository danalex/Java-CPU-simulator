package cpusimulator.code;

import java.util.ArrayList;

/**
 * Instruction enum. Represents possible instructions in this CPU model.
 * Used in assembling/disassembling, not in code execution.
 * 
 * @author Daniel Alexandre 2011
 */
public class Instruction {
    
    public static ArrayList<Instruction> instances = new ArrayList<Instruction>();
    
    // HERE IS: name of instruction and operand types needed for it
    // See Operand class and Operand.Type enum to get more description about operands and their types
    public static final Instruction HALT = new Instruction(0, "HALT");
    public static final Instruction LOAD = new Instruction(1, "LOAD", Operand.Type.REGISTER, Operand.Type.MEMADDR);
    public static final Instruction STORE = new Instruction(2, "STORE", Operand.Type.MEMADDR, Operand.Type.REGISTER);
    public static final Instruction ADDI = new Instruction(3, "ADDI", Operand.Type.REGISTER, Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction ADDF = new Instruction(4, "ADDF", Operand.Type.REGISTER, Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction MOVE = new Instruction(5, "MOVE", Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction NOT = new Instruction(6, "NOT", Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction AND = new Instruction(7, "AND", Operand.Type.REGISTER, Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction OR = new Instruction(8, "OR", Operand.Type.REGISTER, Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction XOR = new Instruction(9, "XOR", Operand.Type.REGISTER, Operand.Type.REGISTER, Operand.Type.REGISTER);
    public static final Instruction INC = new Instruction(10, "INC", Operand.Type.REGISTER);
    public static final Instruction DEC = new Instruction(11, "DEC", Operand.Type.REGISTER);
    public static final Instruction ROTATE = new Instruction(12, "ROTATE", Operand.Type.REGISTER, Operand.Type.NUMBER4, Operand.Type.NUMBER4);
    public static final Instruction JUMP = new Instruction(13, "JUMP", Operand.Type.REGISTER, Operand.Type.LABEL);
    
    public static Instruction valueOf(String name) {
        for(Instruction ins : instances) if(ins.getName().equals(name)) return ins;
        throw new IllegalArgumentException();
    }
    
    public static Instruction[] values() {
        return instances.toArray(new Instruction[instances.size()]);
    }
    
    private int ordinal;
    /** Instruction name, specified above as first argument */
    private String instructionName;
    /** Instruction types, specified above as second, third and more argument */
    private Operand.Type[] types;
    
    /**
     * Creating new instruction with specified name and operand types.
     * Inside enum only because this is an enum, not class.
     * @param name instruction name, will be used in assembler/disassembler
     * @param types instruction's operands types
     */
    private Instruction(int ordinal, String name, Operand.Type... types) {
        this.ordinal = ordinal;
        this.instructionName = name;
        this.types = types;
        instances.add(this);
    }
    
    /**
     * Returns instruction's name.
     * @return instruction's name
     */
    public String getName() {
        return instructionName;
    }
    
    /**
     * Creates instruction's binary representation, using specified operands.
     * Operands' types will be validated according to instruction specification.
     * @param operands instruction's operands
     * @return instruction's binary representation
     * @throws AssembleException if operand type is wrong or there is too much
     * or too few operands.
     * @throws NullPointerException if operands is null
     */
    public short getBinary(Operand[] operands) throws AssembleException {
        if(operands == null) throw new NullPointerException();
        // verify operands count
        if(operands.length != types.length) throw new AssembleException(getName() + ": " + " " + types.length + " operands expected, but " + operands.length + " got");
        int bits = 4; // each instruction have at least 4 bits - for it's indentification.
        int data = ordinal; // instruction identification data, just a number in 0 to 15 range, inclusive
        for(int i = 0; i < operands.length; i ++) {
            // for each operand: check it type
            if(operands[i].getType() != types[i]) throw new AssembleException(getName() + ": " + " " + getTh(i + 1) + " operand should have " + types[i].getName() + " type, but it have " + operands[i].getType().getName() + " type");
            // if operand's data is too large to compose it in 2-byte field
            if((operands[i].getData() & (1 << operands[i].getType().getSize()) - 1) != operands[i].getData()) throw new IllegalArgumentException("wrong operand data size");
            // compose operand data into instruction binary representation
            bits += operands[i].getType().getSize();
            data = (data << operands[i].getType().getSize()) | (operands[i].getData());
        }
        // if we got instruction which cann't lay in 2 bytes
        if(bits > 16) throw new RuntimeException("too much bits for instruction");
        // align instruction to 2-byte align, according to 16-bit memory model
        data <<= 16 - bits;
        // return instruction's representation
        return (short)data;
    }
    
    /**
     * Generates operands from it's binary representation.
     * @param opcode instruction's binary representation
     * @return disassembled array of operands
     */
    public Operand[] getOperands(short opcode) {
        // remove instruction number
        int iop = (opcode & 0xFFFF) << 4;
        // creating ops array according to instruction's specification
        Operand[] ops = new Operand[types.length];
        for(int i = 0; i < ops.length; i ++) {
            // parse operand current
            ops[i] = types[i].createOperand(iop, 16);
            // and shift out instruction's bits because it was used
            iop <<= types[i].getSize();
        }
        // return resulting operand array
        return ops;
    }
    
    /**
     * Just helper method.
     * @param num natural number
     * @return number's counting form
     */
    private String getTh(int num) {
        return num == 1 ? "1st" : num == 2 ? "2nd" : num == 3 ? "3rd" : num + "th";
    }
}
