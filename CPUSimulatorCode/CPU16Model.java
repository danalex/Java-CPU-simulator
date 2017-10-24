package cpusimulator;

import cpusimulator.memory.BreakpointMemory16;
import cpusimulator.memory.DataMemory16;
import cpusimulator.memory.Memory16;

/**
 * This is 16-bit CPU model with ALU and attached memory. Have 14 instructions
 * only. This class emulates CPU execution.
 * 
 * @author Daniel Alexandre 2011
 */
public class CPU16Model {
    
    /** Memory with which CPU will work. */
    private Memory16 memory;
    /** Program counter, points to next instruction will executed. Maximum count of commands - 256, because byte type is used. */
    private byte PC;
    /** Instruction register, contains current executing instruction when executing or last executed instruction when not executing */
    private short IR;
    /** This object can be called and it's called when CPU executes HALT command. */
    private Runnable haltHandler;
    /** This object can be called and it's called when CPU faced unknown instruction. */
    private Runnable wrongInstructionHandler;
    /** Array with 16-bit register values, used to store values. */
    private BreakpointMemory16 REG;
    
    /**
     * Creates new 16-bit CPU model with specified memory, initial program counter, specified hand handler and wrong instruction handler.
     * @param connectedMemory memory with which CPU will work, shouldn't be null
     * @param startPC initial program counter, should be 0
     * @param haltHandler callable object to handle halt command execution, shouldn't be null
     * @param wrongInstructionFailedHandler callable object to handle wrong instructions, shouldn't be null
     * @param breakpointReadCheck callable object to handle breakpoint memory errors
     * @throws NullPointerException if connectedMemory, haltHandler or wrongInstructionFailedHandler is null
     */
    public CPU16Model(Memory16 connectedMemory, byte startPC, Runnable haltHandler, Runnable wrongInstructionFailedHandler, Runnable breakpointReadCheck) {
        if(connectedMemory == null) throw new NullPointerException();
        if(haltHandler == null) throw new NullPointerException();
        if(wrongInstructionFailedHandler == null) throw new NullPointerException();
        memory = connectedMemory;
        PC = startPC;
        this.haltHandler = haltHandler;
        wrongInstructionHandler = wrongInstructionFailedHandler;
        Runnable invalidAddress = new Runnable() {
            @Override public void run() { throw new RuntimeException("Invalid register address"); }
        };
        REG = new BreakpointMemory16(new DataMemory16(16, invalidAddress), 16, breakpointReadCheck);
    }
    
    /**
     * Returns registers memory with breakpoints support.
     * @return memory with breakpoints support
     */
    public BreakpointMemory16 getRegistersBreakpoints() {
        return REG;
    }
    
    /**
     * Cleans all registers, PC and IR to zero.
     */
    public void reset() {
        for(int i = 0; i < 16; i ++) REG.write((byte)i, (short)0);
        PC = 0;
        IR = 0;
    }
    
    /**
     * Reads instruction from memory and stores it into IR.
     */
    public void fetchInstruction() {
        IR = memory.read(PC);
    }
    
    /**
     * Emulates just one instruction and exits from the method. Can call wrongInstructionHandler on wrong instruction appearance.
     */
    public void emulateInstruction() {
        PC ++;
        int instruction = IR & 0xFFFF;
        switch(instruction >>> 12) { // decoding: gets leftest 4 bits (in other words, left digit in command hex representation)
            case 0: // HALT
                haltHandler.run();
                break;
            case 1: // LOAD <reg>, <mem>
                REG.write((byte)((instruction >>> 8) & 0xf), memory.read((byte)instruction));
                break;
            case 2: // STORE <mem>, <reg>
                memory.write((byte)(instruction >>> 4), REG.read((byte)(instruction & 0xf)));
                break;
            case 3: // ADDI <reg>, <reg>, <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(REG.read((byte)((instruction >>> 4) & 0xf)) + REG.read((byte)(instruction & 0xf))));
                break;
            case 4: // ADDF <reg>, <reg>, <reg>
                // not yet supported
                wrongInstructionHandler.run();
                break;
            case 5: // MOVE <reg>, <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), REG.read((byte)((instruction >>> 4) & 0xf)));
                break;
            case 6: // NOT <reg>, <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(~REG.read((byte)((instruction >>> 4) & 0xf))));
                break;
            case 7: // AND <reg>, <reg>, <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(REG.read((byte)((instruction >>> 4) & 0xf)) & REG.read((byte)(instruction & 0xf))));
                break;
            case 8: // OR <reg>, <reg>, <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(REG.read((byte)((instruction >>> 4) & 0xf)) | REG.read((byte)(instruction & 0xf))));
                break;
            case 9: // XOR <reg>, <reg>, <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(REG.read((byte)((instruction >>> 4) & 0xf)) ^ REG.read((byte)(instruction & 0xf))));
                break;
            case 10: // INC <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(REG.read((byte)((instruction >>> 8) & 0xf)) + 1));
                break;
            case 11: // DEC <reg>
                REG.write((byte)((instruction >>> 8) & 0xf), (short)(REG.read((byte)((instruction >>> 8) & 0xf)) - 1));
                break;
            case 12: // ROTATE <reg>, <count>, <count>
                int value = REG.read((byte)((instruction >>> 8) & 0xf)) & 0xffff;
                if((instruction & 0xf) == 0) REG.write((byte)((instruction >>> 8) & 0xf), (short)((value << ((instruction >>> 4) & 0xf)) | (value >>> (16 - ((instruction >>> 4) & 0xf)))));
                else if((instruction & 0xf) == 1) REG.write((byte)((instruction >>> 8) & 0xf), (short)((value >>> ((instruction >>> 4) & 0xf)) | (value << (16 - ((instruction >>> 4) & 0xf)))));
                else wrongInstructionHandler.run();
                break;
            case 13: // AND <reg>, <reg>, <reg>
                if(REG.read((byte)0) != REG.read((byte)((instruction >>> 8) & 0xf))) PC = (byte)instruction;
                break;
        }
    }
    
    /**
     * Returns value of specified register.
     * @param reg register number, should be from 0 to 15 inclusive
     * @return containing data
     * @throws IllegalArgumentException if reg is out of 0..15 range
     */
    public short getRegValue(int reg) {
        if(reg < 0 || reg >= 16) throw new IllegalArgumentException();
        return REG.read((byte)reg);
    }

    /**
     * Sets value of specified register.
     * @param reg register number, should be from 0 to 15 includive
     * @param newValue data to write to register
     * @throws IllegalArgumentException if reg is out of 0..15 range
     */
    public void setRegValue(int reg, short newValue) {
        if(reg < 0 || reg >= 16) throw new IllegalArgumentException();
        REG.write((byte)reg, newValue);
    }
    
    /**
     * Returns program counter index: memory address which points to cell with instruction which
     * will be executed on next emulateInstruction() method call.
     * @return memory address
     */
    public byte getPCValue() {
        return PC;
    }
    
    /**
     * Sets program counter address to specified value
     * @param newPC new program counter address
     */
    public void setPCValue(byte newPC) {
        PC = newPC;
    }
    
    /**
     * Returns currently loaded instruction in the instruction register (IR).
     * @return currently executing/executed instruction
     */
    public short getIRValue() {
        return IR;
    }
}
