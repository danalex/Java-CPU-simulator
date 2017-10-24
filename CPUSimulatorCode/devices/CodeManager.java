package cpusimulator.code;

import cpusimulator.memory.InstructionMemory16;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * This class used to manage machine code in memory.
 * There is assembling, disassembling methods and address-to-label and label-to-
 * address convertion capabilities.
 * 
 * @author Daniel Alexandre 2011
 */
public class CodeManager {
    
    /** Memory used to write and read instructions. */
    private InstructionMemory16 memory;
    /** Labels offsets by it's names. */
    private HashMap<String, Integer> labelNames = new HashMap<String, Integer>();
    /** Labels names by it's offsets. */
    private HashMap<Integer, String> labelOffsets = new HashMap<Integer, String>();
    /** Current instruction offset. Indicates cell with next instruction to disassemble or
      * cell to which next assembled instruction will be written. */
    private byte offset;
    
    /**
     * Creates a new code manager which will do management on specified memory
     * and with specified address.
     * @param memory instruction memory which will be modified by this manager
     * @param offset current instruction offset, can be changed
     * @throws NullPointerException if memory is null
     */
    public CodeManager(InstructionMemory16 memory, byte offset) {
        if(memory == null) throw new NullPointerException();
        this.memory = memory;
        this.offset = offset;
    }
    
    /**
     * Adds label to label table if it isn't exists or causes exception otherwise.
     * @param label label name
     * @param offset label code address
     * @throws IllegalStateException if label with the same name OR offset is already available
     */
    public synchronized void addLabel(String label, int offset) {
        if(labelNames.containsKey(label)) throw new IllegalArgumentException("Already have that label");
        Integer off = new Integer(offset);
        if(labelOffsets.containsKey(off)) throw new IllegalArgumentException("Already have that label");
        labelNames.put(label, off);
        labelOffsets.put(off, label);
    }
    
    /**
     * Removes label by it's name or does nothing if table isn't contains label with specified name.
     * @param label name of label which have to be removed
     */
    public synchronized void removeLabel(String label) {
        Integer offset = labelNames.get(label);
        labelNames.remove(label);
        labelOffsets.remove(offset);
    }
    
    /**
     * Removes label by it's code offset or does nothing if table isn't contains label with specified code offset.
     * @param offset code offset of label which have to be removed
     */
    public synchronized void removeLabel(int offset) {
        Integer off = new Integer(offset);
        String name = labelOffsets.get(off);
        labelNames.remove(name);
        labelOffsets.remove(off);
    }
    
    /**
     * Does instruction assembling. Throws exception on fail or write instruction
     * code to memory on success. After writing offset will be moved to the next
     * memory cell (it will be incremented by 1).
     * @param instruction instruction's text representation
     * @throws AssembleException if there is errors in instruction's text representation
     */
    public synchronized void assembleInstruction(String instruction) throws AssembleException {
        instruction = instruction.trim(); // skips leading and trailing spaces
        // try to cut out instruction's name
        StringTokenizer tokenizer = new StringTokenizer(instruction, " \t");
        if(!tokenizer.hasMoreTokens()) throw new AssembleException("Instruction name expected");
        String insName = tokenizer.nextToken().trim().toLowerCase();
        // yes, we have it. now try to parse operands, separated by comma. we need new tokenizer now
        tokenizer = new StringTokenizer(instruction.substring(insName.length()).trim(), ",");
        Operand[] ops = new Operand[tokenizer.countTokens()];
        for(int i = 0; i < ops.length; i ++) {
            // try to parse operand
            ops[i] = Operand.parse(tokenizer.nextToken().trim());
            if(ops[i].getType() == Operand.Type.LABEL) {
                // if operand's type is address, try to replace label's name by it's address
                Integer off = labelNames.get(ops[i].getString());
                if(off != null) ops[i].replaceLabel(off.intValue());
            }
        }
        // ok, we have correctly parsed operands. let write instruction's code to memory
        synchronized(memory) {
            memory.unlock(); // this allows to write instructions to code memory
            boolean thr = false; // indicates that we have instruction
            try {
                Instruction ins = Instruction.valueOf(insName.toUpperCase()); // it throws exception if there is no such instruction with specified name
                thr = true;
                memory.write(offset, ins.getBinary(ops)); // creating instruction from operands and writing it to memory
            } catch(IllegalArgumentException exception) {
                if(thr) throw exception;
                throw new AssembleException("No such instruction");
            } finally {
                memory.lock(); // in any case (success or fail), we have to lock instruction memory to avoid code segment corruption
            }
        }
        offset ++; // moves pointer to the next instruction
    }
    
    /**
     * Does instruction disassembling. Returns null if disassembling failed or
     * instruction's string representation if disassembling was completed OK.
     * Always increases code address by 1.
     * @return instruction ASM representation
     */
    public synchronized String disassembleInstruction() {
        // reading instruction's binary representation
        short opcode = memory.read(offset);
        offset ++;
        // determining instruction number
        int opindex = (opcode >>> 12) & 0xF;
        // checking for it's correctness
        if(opindex >= Instruction.values().length) return null;
        // creating instruction by it's number
        Instruction instr = Instruction.values()[opindex];
        // passing operands' binary data to it
        Operand[] ops = instr.getOperands(opcode);
        // now building instruction's ASM representation
        StringBuilder builder = new StringBuilder();
        builder.append(instr.getName()); // adding name to stringbuffer
        int index = 0;
        // ... and operands with commas and spaces in needed positions
        for(Operand op : ops) builder.append((index ++) == 0 ? " " : ", ").append(op.getAsm(labelOffsets));
        // then convert stringbuffer to string and return disassembled result
        return builder.toString().trim();
    }
    
    /**
     * Changes memory address to new one.
     * @param newOffset new memory address to read and write instructions
     */
    public synchronized void gotoOffset(byte newOffset) {
        offset = newOffset;
    }
    
    /**
     * Returns memory address (instruction reading source / writing destination).
     * @return memory address
     */
    public synchronized int getOffset() {
        return offset;
    }
}
