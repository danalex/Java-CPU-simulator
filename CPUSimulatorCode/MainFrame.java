package cpusimulator;

import cpusimulator.code.AssembleException;
import cpusimulator.code.CodeManager;
import cpusimulator.devices.Display;
import cpusimulator.devices.Keyboard;
import cpusimulator.devices.MemoryCard;
import cpusimulator.memory.BreakpointMemory16;
import cpusimulator.memory.DataMemory16;
import cpusimulator.memory.InstructionMemory16;
import cpusimulator.memory.VirtualMemory16;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;

/**
 * This is a main form and a start point of the application.
 * This form have a CPU model and contains elements to rule that model.
 * All results (memory values, registers, code) will immediately shown for user.
 * 
 * @author Daniel Alexandre 2011
 */
public class MainFrame extends javax.swing.JFrame {
    
    /** Our CPU model. */
    private CPU16Model model;
    /** Memory to store data. */
    private DataMemory16 datamem;
    /** Memory to store code. */
    private InstructionMemory16 codemem;
    /** Breakpointed data memory. Used to add data memory breakpoints. */
    private BreakpointMemory16 bdatamem;
    /** Breakpointed code memory. Used to add code mmeory breakpoints. */
    private BreakpointMemory16 bcodemem;
    /** This is our display device, attached to virtual memory. */
    private Display display;
    /** This is our keyboard device, attached to virtual memory. */
    private Keyboard keyboard;
    /** Code manager is used to assemble/disassemble code. */
    private CodeManager manager;
    /** This flag used to avoid endless recursion caused by data changing and data handling in the same class. */
    private boolean instructionTableChangeAccept = true;
    /** This flag used to avoid endless recursion caused by data changing and data handling in the same class. */
    private boolean memoryTableChangeAccept = true;
    /** This flag used to avoid endless recursion caused by data changing and data handling in the same class. */
    private boolean registersTableChangeAccept = true;
    /** This flag used to avoid endless recursion caused by data changing and data handling in the same class. */
    private boolean breakpointsTableChangeAccept = true;
    /** This flag indicates CPU interruption state. */
    private boolean cpuInterrupted = false;
    /** This flag indicates CPU running state. */
    private boolean cpuRunning = false;
    /** This flag indicates that we have wrong breakpoint address in some case. */
    private boolean breakpointReadFail = false;
    /** Our source editor dialog instance. */
    private AsmSourceDialog sourceEditor = new AsmSourceDialog(this, true, new Runnable() {
        @Override public void run() { cancelEditSource(); }
    }, new Runnable() {
        @Override public void run() { acceptEditSource(); }
    });
    /** Array of created breakpoints. */
    private ArrayList<Breakpoint16> breakpoints = new ArrayList<Breakpoint16>();
    /** Breakpoint which is created and used only when we run code to cursor. */
    private Breakpoint16 toCursorBreakpoint;

    /**
     * Creates new MainFrame.
     */
    public MainFrame() {
        initComponents(); // initializes components of main form
        
        // invalid memory address used to handle invalid memory errors
        Runnable invalidMemoryAddress = new Runnable() {
            public void run() { handleError("Invalid memory address!"); }
        };
        
        // creating virtual memory from 0x00 to 0xFF (it cann't be used until we haven't attached memory to it)
        VirtualMemory16 vmemory = new VirtualMemory16(256, new Runnable() {
            public void run() { handleError("Unmapped block error!"); }
        }, invalidMemoryAddress);
        // creating handler to handle breakpoint wrong operations
        Runnable breakpointReadCheck = new Runnable() {
            @Override public void run() { breakpointReadFail = true; }
        };
        
        // creating data memory: from 0x40 to 0xFD (length - 0xBE)
        datamem = new DataMemory16(0xBE, invalidMemoryAddress);
        // creating breakpointed memory using data memory
        bdatamem = new BreakpointMemory16(datamem, 0xBE, breakpointReadCheck);
        
        // handler which will handle attempts to change code (can happen if we will have wrong assembler code)
        Runnable changeCodeAttempt = new Runnable() {
            public void run() { handleError("Attempt to change code!"); }
        };
        
        // creating instruction memory: from 0x00 to 0x3F (length - 0x40)
        codemem = new InstructionMemory16(0x40, invalidMemoryAddress, changeCodeAttempt);
        // creating breakpointed memory using instruction memory
        bcodemem = new BreakpointMemory16(codemem, 0x40, breakpointReadCheck);
        codemem.lock(); // lock memory to avoid code corruption by asm code execution
        
        // creating our display
        display = new Display(false, invalidMemoryAddress) {
            @Override public void numberWrited(short number) { handleOutput(String.valueOf(number)); }
        };
        // creating our keyboard
        keyboard = new Keyboard(new Runnable() {
            public void run() { handleError("Invalid memory address"); }
        });
        keyboard.setLocking(true); // set keyboard locking by-default
        
        final int CODE_OFFSET = 0x0;
        final int DATA_OFFSET = CODE_OFFSET + codemem.getLength();
        final int KEYBOARD_OFFEST = 0xFE;
        final int DISPLAY_OFFSET = 0xFF;
        
        // mapping memory cards and devices to virtual memory
        vmemory.map(new MemoryCard(bcodemem, codemem.getLength()), 0, CODE_OFFSET);
        vmemory.map(new MemoryCard(bdatamem, datamem.getLength()), 0, DATA_OFFSET);
        // and there we have to map two future devices: keyboard and display to 0xFE and 0xFF address
        // before we will add devices, any reads from or writes to two last cells will cause "Unmapped block error!"
        vmemory.map(keyboard, 0, KEYBOARD_OFFEST);
        vmemory.map(display, 0, DISPLAY_OFFSET);
        
        // creating instruction manager which we will use to assemble/disassemble code
        manager = new CodeManager(codemem, (byte)0);
        
        // creating cpu which operates with our virtual memory
        model = new CPU16Model(vmemory, (byte)0, new Runnable() {
            public void run() { cpuInterrupted = true; handleInfo("Program was halted!"); }
        }, new Runnable() {
            public void run() { handleError("Wrong instruction found!"); }
        }, breakpointReadCheck);
        // now we can run cpu by invoking model.emulateInstruction()
        
        registersTable.getModel().addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) { registersTableChanged(e); }
        });
        
        // make memory table model to use it in the JTable swing component
        Object[][] memoryTableData = new Object[datamem.getLength()][2];
        for(int i = 0; i < datamem.getLength(); i ++) memoryTableData[i][0] = "0x" + Integer.toString(DATA_OFFSET + i, 16).toUpperCase();
        memoryTable.setModel(new DefaultTableModel(memoryTableData, new String [] { "Addr", "Value" }) {
            Class[] types = new Class [] { Object.class, Object.class };
            boolean[] canEdit = new boolean [] { false, true };

            @Override public Class getColumnClass(int columnIndex) { return types [columnIndex]; }
            @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return canEdit [columnIndex]; }
        });
        // add table changing listener
        memoryTable.getModel().addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) { memoryTableChanged(e); }
        });
        
        // make instruction table model to use it in the JTable swing component
        Object[][] instructionsTableData = new Object[codemem.getLength()][5];
        for(int i = 0; i < codemem.getLength(); i ++) instructionsTableData[i][0] = "0x" + Integer.toHexString(i).toUpperCase();
        instructionsTable.setModel(new DefaultTableModel(instructionsTableData, new String[] { "Addr", "Data", "Label", "Instruction", "Good" }) {
            Class[] types = new Class [] { Object.class, Object.class, Object.class, Object.class, Boolean.class };
            boolean[] canEdit = new boolean [] { false, true, true, true, false };

            @Override public Class getColumnClass(int columnIndex) { return types [columnIndex]; }
            @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return canEdit [columnIndex]; }
        });
        // adding table changing listener
        instructionsTable.getModel().addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) { instructionsTableChanged(e); }
        });
        // some cosmetical changes
        instructionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int totalWidth = instructionsTable.getColumnModel().getTotalColumnWidth();
        instructionsTable.getColumnModel().getColumn(0).setPreferredWidth(totalWidth * 1 / 10);
        instructionsTable.getColumnModel().getColumn(1).setPreferredWidth(totalWidth * 1 / 10);
        instructionsTable.getColumnModel().getColumn(2).setPreferredWidth(totalWidth * 2 / 10);
        instructionsTable.getColumnModel().getColumn(3).setPreferredWidth(totalWidth * 5 / 10);
        instructionsTable.getColumnModel().getColumn(4).setPreferredWidth(totalWidth * 1 / 10);
        instructionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        // adding listenern to breakpoints table
        breakpointsWatchesTable.getModel().addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) { breakpointsTableChanged(e); }
        });
        
        // updating all data on the form to have zeros at start of app
        updateInputLock();
        updateMemory();
        updateRegisters();
        updatePCandIR();
        updateInstructions();
        updateBreakpoints();
        
        handleInfo("Initialization complete.");
    }
    
    /**
     * This method should be called every time we have some error - a serious problem.
     * @param message information about a problem
     */
    private void handleError(String message) {
        synchronized(terminalTextArea) {
            terminalTextArea.setText(terminalTextArea.getText() + ("Error: " + message + "\n"));
            terminalTextArea.setCaretPosition(terminalTextArea.getDocument().getLength());
        }
    }
    
    /**
     * This method should be called every time we have some information which can be useful by user.
     * @param message useful information
     */
    private void handleInfo(String message) {
        synchronized(terminalTextArea) {
            terminalTextArea.setText(terminalTextArea.getText() + ("Info: " + message + "\n"));
            terminalTextArea.setCaretPosition(terminalTextArea.getDocument().getLength());
        }
    }
    
    /**
     * This method should be called every time when compiler detect errors in assembler code.
     * @param message assembling information
     */
    private void handleAsmError(String message) {
        synchronized(terminalTextArea) {
            terminalTextArea.setText(terminalTextArea.getText() + ("Asm error: " + message + "\n"));
            terminalTextArea.setCaretPosition(terminalTextArea.getDocument().getLength());
        }
    }
    
    /**
     * This method should be called every time when we have some output from display.
     * @param message display output
     */
    private void handleOutput(String message) {
        synchronized(terminalTextArea) {
            terminalTextArea.setText(terminalTextArea.getText() + (">> " + message + "\n"));
            terminalTextArea.setCaretPosition(terminalTextArea.getDocument().getLength());
        }
    }
    
    /**
     * This method should be called every time when we have some input from keyboard.
     * @param message keyboard input
     */
    private void handleInput(String message) {
        synchronized(terminalTextArea) {
            terminalTextArea.setText(terminalTextArea.getText() + ("<< " + message + "\n"));
            terminalTextArea.setCaretPosition(terminalTextArea.getDocument().getLength());
        }
    }
    
    /** Updates memory table. */
    private void updateMemory() {
        memoryTableChangeAccept = false;
        for(int i = 0; i < datamem.getLength(); i ++) memoryTable.getModel().setValueAt("0x" + Integer.toString(datamem.read((byte)i) & 0xFFFF, 16).toUpperCase(), i, 1);
        memoryTableChangeAccept = true;
    }
    
    /** Updates registers table. */
    private void updateRegisters() {
        registersTableChangeAccept = false;
        for(int i = 0; i < 16; i ++) registersTable.getModel().setValueAt("0x" + Integer.toString(model.getRegValue(i), 16).toUpperCase(), i, 1);
        registersTableChangeAccept = true;
    }
    
    /** Updates PC and IR textfields. */
    private void updatePCandIR() {
        programCounterTextField.setText("0x" + Integer.toString(model.getPCValue() & 0xFF, 16).toUpperCase());
        instructionRegisterTextField.setText("0x" + Integer.toString(model.getIRValue() & 0xFFFF, 16).toUpperCase());
        instructionsTable.getSelectionModel().setSelectionInterval(model.getPCValue() & 0xFF, model.getPCValue() & 0xFF);
    }
    
    /** Updates instructions table. */
    private void updateInstructions() {
        instructionTableChangeAccept = false;
        manager.gotoOffset((byte)0);
        // fill static values at first
        for(int i = 0; i < codemem.getLength(); i ++) {
            instructionsTable.getModel().setValueAt("0x" + Integer.toHexString(codemem.read((byte)i) & 0xFFFF).toUpperCase(), i, 1);
            instructionsTable.getModel().setValueAt(null, i, 3);
            instructionsTable.getModel().setValueAt(new Boolean(false), i, 4);
        }
        int offset;
        // and secondary, disassemble instruction
        while((offset = manager.getOffset()) != codemem.getLength()) {
            String disassembled = manager.disassembleInstruction();
            instructionsTable.getModel().setValueAt(disassembled == null ? "???" : disassembled, offset, 3);
            instructionsTable.getModel().setValueAt(new Boolean(disassembled != null), offset, 4);
        }
        instructionTableChangeAccept = true;
    }
    
    /** Updates breakpoints table. */
    private void updateBreakpoints() {
        breakpointsTableChangeAccept = false;
        DefaultTableModel model = (DefaultTableModel)breakpointsWatchesTable.getModel();
        model.setNumRows(0); // remove all rows
        for(Breakpoint16 breakpoint : breakpoints) {
            // and add rows generated from breakpoints info in our list of breakpoints
            model.addRow(new Object[] {
                "0x" + Integer.toHexString(breakpoint.getAddress()).toUpperCase(), 
                "0x" + Integer.toHexString(breakpoint.getValue() & 0xFFFF).toUpperCase(),
                breakpoint.getType() == Breakpoint16.BREAKPOINT_CHANGE ? "Change" :
                breakpoint.getType() == Breakpoint16.BREAKPOINT_READ ? "Read" :
                breakpoint.getType() == Breakpoint16.BREAKPOINT_WRITE ? "Write" : "??",
                breakpoint.getMemory() == codemem ? "Code" :
                breakpoint.getMemory() == datamem ? "Data" : "Reg",
                new Boolean(breakpoint.isEnabled()),
            });
        }
        breakpointsTableChangeAccept = true;
    }
    
    /**
     * Method called when data is inputed from bottom textfield which used as keyboard.
     * @param text inputed text
     */
    private void dataInputed(String text) {
        try {
            keyboard.inputNumber(Integer.parseInt(text)); // send typed data to keyboard device (it's just emulation)
            handleInput(text); // print our inputed text to console
            inputTextField.setText(""); // and clear input textfield
        } catch(NumberFormatException exception) {
            handleError("Number expected!");
        }
    }
    
    /** Makes keyboard locked or unlocked depends on lock-input checkbox state. */
    private void updateInputLock() {
        keyboard.setLocking(inputLockCheckBox.isSelected());
    }
    
    /**
     * Called every time when instructions table have been changed.
     * @param e info about event
     */
    private void instructionsTableChanged(TableModelEvent e) {
        if(instructionTableChangeAccept) {
            int column = e.getColumn();
            switch(column) {
                case 0: // address changed - strange, because address changing is not allowed
                    throw new RuntimeException("row is not editable");
                case 1: // data changed: let disassemble instruction
                {
                    int row = e.getFirstRow();
                    String data = (String)instructionsTable.getValueAt(row, column);
                    if(data == null || (data = data.trim()).isEmpty()) data = "";
                    if(!data.isEmpty()) {
                        try {
                            int datai;
                            if(data.startsWith("0x")) datai = Integer.parseInt(data.substring(2), 16);
                            else datai = Integer.parseInt(data);
                            datai &= 0xFFFF;
                            codemem.unlock();
                            codemem.write((byte)row, (short)datai);
                            codemem.lock();
                            manager.gotoOffset((byte)row);
                            String disassembled = manager.disassembleInstruction();
                            instructionTableChangeAccept = false;
                            instructionsTable.setValueAt("0x" + Integer.toHexString(datai).toUpperCase(), row, 1);
                            instructionsTable.setValueAt(disassembled == null ? "???" : disassembled, row, 3);
                            instructionsTable.setValueAt(new Boolean(disassembled != null), row, 4);
                            instructionTableChangeAccept = true;
                        } catch(NumberFormatException exception) {
                        }
                    }
                    updateBreakpoints();
                    break;
                }
                case 2: // label changed: just make it correct and add label to manager
                {
                    int row = e.getFirstRow();
                    String label = (String)instructionsTable.getValueAt(row, column);
                    if(label == null || (label = label.trim()).isEmpty() || label.equals(":")) {
                        label = "";
                    } else {
                        if(label.endsWith(":")) label = label.substring(0, label.length() - 1).trim();
                    }
                    instructionTableChangeAccept = false;
                    instructionsTable.setValueAt(label, row, column);
                    instructionTableChangeAccept = true;
                    manager.removeLabel(row);
                    if(!label.isEmpty()) manager.addLabel(label, row);
                    break;
                }
                case 3: // instruction changed: let assemble instruction
                {
                    int row = e.getFirstRow();
                    String instruction = (String)instructionsTable.getValueAt(row, column);
                    if(instruction == null || (instruction = instruction.trim()).isEmpty()) instruction = "";
                    if(!instruction.isEmpty()) {
                        boolean good;
                        try {
                            manager.gotoOffset((byte)row);
                            manager.assembleInstruction(instruction);
                            good = true;
                        } catch(AssembleException exception) {
                            handleAsmError(exception.getMessage());
                            good = false;
                        }
                        instructionTableChangeAccept = false;
                        instructionsTable.setValueAt("0x" + Integer.toHexString(codemem.read((byte)row) & 0xFFFF).toUpperCase(), row, 1);
                        instructionsTable.setValueAt(instruction, row, 3);
                        instructionsTable.setValueAt(new Boolean(good), row, 4);
                        instructionTableChangeAccept = true;
                    }
                    updateBreakpoints();
                    break;
                }
                case 4:
                    throw new RuntimeException("row is not editable");
                default:
                    throw new RuntimeException("not implemented since table have 2 editable columns");
            }
        }
    }

    /**
     * Called each time when memory table have been changed.
     * @param e event information
     */
    private void memoryTableChanged(TableModelEvent e) {
        if(memoryTableChangeAccept) {
            int column = e.getColumn();
            switch(column) {
                case 0: // address changed: strange, because address isn't allowed to edit
                    throw new RuntimeException("row is not editable");
                case 1: // data changed: let write changed data to memory
                {
                    int row = e.getFirstRow();
                    String data = (String)memoryTable.getValueAt(row, column);
                    if(data == null || (data = data.trim()).isEmpty()) data = "";
                    if(!data.isEmpty()) {
                        try {
                            int datai;
                            if(data.startsWith("0x")) datai = Integer.parseInt(data.substring(2), 16);
                            else datai = Integer.parseInt(data);
                            datai &= 0xFFFF;
                            datamem.write((byte)row, (short)datai);
                            memoryTableChangeAccept = false;
                            memoryTable.setValueAt("0x" + Integer.toHexString(datai).toUpperCase(), row, 1);
                            memoryTableChangeAccept = true;
                        } catch(NumberFormatException exception) {
                        }
                    }
                    updateBreakpoints();
                    break;
                }
                default:
                    throw new RuntimeException("not implemented since table have 1 editable column");
            }
        }
    }

    /**
     * Called each time when user changes registers table contents.
     * @param e event information
     */
    private void registersTableChanged(TableModelEvent e) {
        if(registersTableChangeAccept) {
            int column = e.getColumn();
            switch(column) {
                case 0: // register name changed: strange, we haven't allowed this
                    throw new RuntimeException("row is not editable");
                case 1: // changed register data: let change it in the cpu
                {
                    int row = e.getFirstRow();
                    String data = (String)registersTable.getValueAt(row, column);
                    if(data == null || (data = data.trim()).isEmpty()) data = "";
                    if(!data.isEmpty()) {
                        try {
                            int datai;
                            if(data.startsWith("0x")) datai = Integer.parseInt(data.substring(2), 16);
                            else datai = Integer.parseInt(data);
                            datai &= 0xFFFF;
                            model.setRegValue(row, (short)datai);
                            registersTableChangeAccept = false;
                            registersTable.setValueAt("0x" + Integer.toHexString(datai).toUpperCase(), row, 1);
                            registersTableChangeAccept = true;
                        } catch(NumberFormatException exception) {
                        }
                    }
                    break;
                }
                default:
                    throw new RuntimeException("not implemented since table have 1 editable column");
            }
        }
    }
    
    /**
     * Called every time when breakpoints table has been changed.
     * @param e event information
     */
    private void breakpointsTableChanged(TableModelEvent e) {
        if(breakpointsTableChangeAccept) {
            int column = e.getColumn();
            switch(column) {
                case 0:
                    throw new RuntimeException("row is not editable");
                case 1:
                    throw new RuntimeException("row is not editable");
                case 2:
                    throw new RuntimeException("row is not editable");
                case 3:
                    throw new RuntimeException("row is not editable");
                case 4: // only breakpoint enabling/disabling is allowed
                {
                    int row = e.getFirstRow();
                    Boolean data = (Boolean)breakpointsWatchesTable.getValueAt(row, column);
                    Breakpoint16 breakpoint = breakpoints.get(row);
                    breakpoint.setEnabled(data.booleanValue());
                    break;
                }
                default:
                    throw new RuntimeException("not implemented since table have 1 editable column");
            }
        }
    }
    
    /** Called every time we change PC field. */
    private void doChangePC() {
        String data = programCounterTextField.getText();
        if(data == null || (data = data.trim()).isEmpty()) data = "";
        if(!data.isEmpty()) {
            try {
                int datai;
                if(data.startsWith("0x")) datai = Integer.parseInt(data.substring(2), 16);
                else datai = Integer.parseInt(data);
                model.setPCValue((byte)datai);
                programCounterTextField.setText("0x" + Integer.toHexString(datai).toUpperCase());
            } catch(NumberFormatException exception) {
            }
        }
    }
    
    /** Called every time we have selected Reset menu. */
    private void doReset() {
        model.setPCValue((byte)0);
        updatePCandIR();
    }
    
    /** Called every time we have selected New menu. */
    private void doNew() {
        codemem.unlock();
        for(int i = 0; i < codemem.getLength(); i ++) codemem.write((byte)i, (short)0);
        codemem.lock();
        for(int i = 0; i < datamem.getLength(); i ++) datamem.write((byte)i, (short)0);
        model.reset();
        updateRegisters();
        updatePCandIR();
        updateMemory();
        updateInstructions();
        updateBreakpoints();
    }
    
    /** Called every time we have selected Step menu. */
    private void doStep() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                cpuInterrupted = false;
                handleInfo("CPU runned...");
                cpuRunning = true;
                model.fetchInstruction();
                if(!cpuInterrupted) model.emulateInstruction();
                cpuRunning = false;
                handleInfo("CPU running finished");
                updateRegisters();
                updatePCandIR();
                updateMemory();
                updateInstructions();
                updateBreakpoints();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
    
    /** Called every time we have selected Run menu */
    private void doRun() {
        if(cpuRunning) {
            handleInfo("CPU already started!");
        }
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    cpuInterrupted = false;
                    cpuRunning = true;
                    handleInfo("CPU runned...");
                    while(cpuRunning) {
                        model.fetchInstruction();
                        if(cpuInterrupted) break;
                        model.emulateInstruction();
                        updateRegisters();
                        updatePCandIR();
                        updateMemory();
                        updateBreakpoints();
                    }
                    if(cpuRunning) handleInfo("CPU running finished");
                    if(!cpuRunning) handleInfo("CPU running stopped");
                } finally {
                    cpuRunning = false;
                    if(toCursorBreakpoint != null) {
                        toCursorBreakpoint.remove();
                        toCursorBreakpoint = null;
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
    
    /** Called every time when we have canceled asm source ediding in the edit asm dialog. */
    private void cancelEditSource() {
        sourceEditor.setVisible(false);
        setVisible(true);
    }
    
    /** Called every time when we have accepted asm source ediding in the edit asm dialog. */
    private void acceptEditSource() {
        String[] asmLines = sourceEditor.getAsmLines();
        if(asmLines.length > instructionsTable.getRowCount()) {
            new InfoMessageDialog(this, "Sorry, this code is too long to store it into memory.").setVisible(true);
            return;
        }
        storeAsmLines(asmLines);
        sourceEditor.setVisible(false);
        setVisible(true);
    }
    
    /** Called every time when user selected "Run to cursor" menu item. */
    private void doRunToCursor() {
        int selectedRow = instructionsTable.getSelectedRow();
        if(selectedRow >= 0) {
            toCursorBreakpoint = bcodemem.createBreakpoint((byte)selectedRow, Breakpoint16.BREAKPOINT_READ);
            toCursorBreakpoint.setEnabled(true);
            toCursorBreakpoint.setHandler(new Runnable() {
                @Override
                public void run() {
                    if(cpuRunning) {
                        cpuInterrupted = true;
                        handleInfo("Cursor is reached");
                    }
                }
            });
            doRun();
        }
    }
    
    /** Called every time when user have selected Stop menu item. */
    private void doStop() {
        cpuRunning = false;
    }
    
    /** Called every time when user have selected "Import data" menu item. */
    private void doImportData() {
        jFileChooser_Import.showOpenDialog(this);
        File file = jFileChooser_Import.getSelectedFile();
        if(file != null) {
            if(!file.exists()) {
                new InfoMessageDialog(this, "File doesn't exists").setVisible(true);
                return;
            }
            if(file.isDirectory()) {
                new InfoMessageDialog(this, "Please, select a file, not a directory").setVisible(true);
                return;
            }
            long length = file.length();
            if(length != datamem.getLength() * 2) {
                new InfoMessageDialog(this, "File must have " + datamem.getLength() * 2 + " bytes of length.").setVisible(true);
                return;
            }
            try {
                short[] data = new short[datamem.getLength()];
                DataInputStream input = new DataInputStream(new FileInputStream(file));
                for(int i = 0; i < data.length; i ++) data[i] = input.readShort();
                input.close();
                for(int i = 0; i < data.length; i ++) datamem.write((byte)i, data[i]);
                handleInfo("Memory data has been loaded ok");
                updateMemory();
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
        }
    }
    
    /** Called every time when user have selected "Import asm code" menu item. */
    private void doImportAsmCode() {
        jFileChooser_Import.showOpenDialog(this);
        File file = jFileChooser_Import.getSelectedFile();
        if(file != null) {
            if(!file.exists()) {
                new InfoMessageDialog(this, "File doesn't exists").setVisible(true);
                return;
            }
            if(file.isDirectory()) {
                new InfoMessageDialog(this, "Please, select a file, not a directory").setVisible(true);
                return;
            }
            String[] asmLines;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                ArrayList<String> linesAL = new ArrayList<String>();
                String line;
                while((line = reader.readLine()) != null) {
                    line = line.trim();
                    if(!line.isEmpty()) linesAL.add(line);
                }
                reader.close();
                asmLines = new String[linesAL.size()];
                for(int i = 0; i < linesAL.size(); i ++) asmLines[i] = linesAL.get(i);
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
                return;
            }
            if(asmLines.length > instructionsTable.getRowCount()) {
                new InfoMessageDialog(this, "Sorry, this code is too long to store it into memory.").setVisible(true);
                return;
            }
            storeAsmLines(asmLines);
            updateInstructions();
            handleInfo("Assembler source file has been loaded");
        }
    }
    
    /** Helper method which assembles and stores asm lines to instructions table. */
    private void storeAsmLines(String[] asmLines) {
        for(int i = 0; i < asmLines.length; i ++) {
            String line = asmLines[i];
            String label = "";
            int colonIndex = line.indexOf(':');
            if(colonIndex >= 0) {
                label = line.substring(0, colonIndex).trim();
                line = line.substring(colonIndex + 1).trim();
            }
            instructionTableChangeAccept = false;
            instructionsTable.setValueAt(label, i, 2);
            instructionTableChangeAccept = true;
            manager.removeLabel(i);
            if(!label.isEmpty()) manager.addLabel(label, i);

            boolean good;
            try {
                manager.gotoOffset((byte)i);
                manager.assembleInstruction(line);
                good = true;
            } catch(AssembleException exception) {
                handleAsmError("Offset 0x" + Integer.toHexString(i & 0xFF).toUpperCase() + ":" + exception.getMessage());
                good = false;
            }
            instructionTableChangeAccept = false;
            instructionsTable.setValueAt("0x" + Integer.toHexString(codemem.read((byte)i) & 0xFFFF).toUpperCase(), i, 1);
            instructionsTable.setValueAt(line, i, 3);
            instructionsTable.setValueAt(new Boolean(good), i, 4);
            instructionTableChangeAccept = true;
        }
    }
    
    /** Called every time when user have selected "Import binary code" menu item. */
    private void doImportBinaryCode() {
        jFileChooser_Import.showOpenDialog(this);
        File file = jFileChooser_Import.getSelectedFile();
        if(file != null) {
            if(!file.exists()) {
                new InfoMessageDialog(this, "File doesn't exists").setVisible(true);
                return;
            }
            if(file.isDirectory()) {
                new InfoMessageDialog(this, "Please, select a file, not a directory").setVisible(true);
                return;
            }
            long length = file.length();
            if(length != codemem.getLength() * 2) {
                new InfoMessageDialog(this, "File must have " + codemem.getLength() * 2 + " bytes of length.").setVisible(true);
                return;
            }
            try {
                short[] data = new short[codemem.getLength()];
                DataInputStream input = new DataInputStream(new FileInputStream(file));
                for(int i = 0; i < data.length; i ++) data[i] = input.readShort();
                input.close();
                codemem.unlock();
                for(int i = 0; i < data.length; i ++) codemem.write((byte)i, data[i]);
                codemem.lock();
                handleInfo("Instruction data has been loaded ok");
                updateInstructions();
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
        }
    }
    
    /** Called every time when user have selected "Export data" menu item. */
    private void doExportData() {
        jFileChooser_Export.showSaveDialog(this);
        File file = jFileChooser_Export.getSelectedFile();
        if(file != null) {
            if(file.exists()) {
                if(!file.delete()) {
                    new InfoMessageDialog(this, "Cann't overwrite file").setVisible(true);
                    return;
                }
            }
            try {
                if(!file.createNewFile()) {
                    new InfoMessageDialog(this, "Cann't create file").setVisible(true);
                    return;
                }
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
            try {
                short[] data = new short[datamem.getLength()];
                for(int i = 0; i < data.length; i ++) data[i] = datamem.read((byte)i);
                DataOutputStream output = new DataOutputStream(new FileOutputStream(file));
                for(int i = 0; i < data.length; i ++) output.writeShort(data[i]);
                output.close();
                handleInfo("Memory data has been saved ok");
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
        }
    }
    
    /** Called every time when user have selected "Export asm code" menu item. */
    private void doExportAsmCode() {
        jFileChooser_Export.showSaveDialog(this);
        File file = jFileChooser_Export.getSelectedFile();
        if(file != null) {
            if(file.exists()) {
                if(!file.delete()) {
                    new InfoMessageDialog(this, "Cann't overwrite file").setVisible(true);
                    return;
                }
            }
            try {
                if(!file.createNewFile()) {
                    new InfoMessageDialog(this, "Cann't create file").setVisible(true);
                    return;
                }
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
            String[] asmLines = loadAsmLines();
            try {
                String separator = System.getProperty("line.separator");
                OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file));
                for(String line : asmLines) {
                    writer.write(line);
                    writer.write(separator);
                }
                writer.close();
                handleInfo("Instruction asm source has been saved ok");
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
        }
    }
    
    /** Called every time when user have selected "Export binary code" menu item. */
    private void doExportBinaryCode() {
        jFileChooser_Export.showSaveDialog(this);
        File file = jFileChooser_Export.getSelectedFile();
        if(file != null) {
            if(file.exists()) {
                if(!file.delete()) {
                    new InfoMessageDialog(this, "Cann't overwrite file").setVisible(true);
                    return;
                }
            }
            try {
                if(!file.createNewFile()) {
                    new InfoMessageDialog(this, "Cann't create file").setVisible(true);
                    return;
                }
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
            try {
                short[] data = new short[codemem.getLength()];
                for(int i = 0; i < data.length; i ++) data[i] = codemem.read((byte)i);
                DataOutputStream output = new DataOutputStream(new FileOutputStream(file));
                for(int i = 0; i < data.length; i ++) output.writeShort(data[i]);
                output.close();
                handleInfo("Instruction data has been saved ok");
            } catch(IOException ioexception) {
                new InfoMessageDialog(this, "An I/O exception has been ocurred.").setVisible(true);
            }
        }
    }
    
    /** Called every time when user have selected "Show asm source" menu item. */
    private void doEditAsmSource() {
        updateInstructions();
        String[] assemblerCode = loadAsmLines();
        sourceEditor.setAsmLines(assemblerCode);
        setVisible(false);
        sourceEditor.setVisible(true);
    }
    
    /** Helper method which loads and disassembles binary code from instructions table. */
    private String[] loadAsmLines() {
        String[] assemblerCode;
        synchronized(instructionsTable) {
            ArrayList<String> lines = new ArrayList<String>();
            for(int i = 0; i < instructionsTable.getRowCount(); i ++) {
                String label = (String)instructionsTable.getValueAt(i, 2);
                String instruction = (String)instructionsTable.getValueAt(i, 3);
                lines.add((label != null && !label.trim().isEmpty() ? label.trim() + ": " : "") + instruction);
            }
            while(lines.size() > 1 && lines.get(lines.size() - 1).equalsIgnoreCase("halt") && lines.get(lines.size() - 2).equalsIgnoreCase("halt")) lines.remove(lines.size() - 1);
            assemblerCode = new String[lines.size()];
            for(int i = 0; i < lines.size(); i ++) assemblerCode[i] = lines.get(i);
        }
        return assemblerCode;
    }
    
    /** Called every time when user have pressed "Add breakpoint" button. */
    private void doAddBreakpoint() {
        int type = breakpointTypeComboBox.getSelectedIndex();
        type = type == 0 ? Breakpoint16.BREAKPOINT_READ : type == 1 ? Breakpoint16.BREAKPOINT_WRITE : type == 2 ? Breakpoint16.BREAKPOINT_CHANGE : -1;
        if(type < 0) throw new RuntimeException("Unknown breakpoint type");
        int memtype = breakpointMemComboBox.getSelectedIndex();
        BreakpointMemory16 memory = memtype == 0 ? model.getRegistersBreakpoints() : memtype == 1 ? bdatamem : memtype == 2 ? bcodemem : null;
        if(memory == null) throw new RuntimeException("Unknown breakpoint memory");
        String addressStr = breakpointAddressTextField.getText();
        if(addressStr == null || (addressStr = addressStr.trim()).isEmpty()) return;
        int address;
        try {
            if(addressStr.startsWith("0x")) address = Integer.parseInt(addressStr.substring(2), 16);
            else address = Integer.parseInt(addressStr);
            memory.read((byte)address);
        } catch(NumberFormatException exception) {
            handleError("Wrong breakpoint address");
            return;
        }
        if(breakpointReadFail == true) {
            breakpointReadFail = false;
            handleError("Breakpoint address is too large or less that zero");
            return;
        }
        final Breakpoint16 breakpoint = memory.createBreakpoint((byte)address, type);
        breakpoint.setEnabled(true);
        breakpoint.setHandler(new Runnable() {
            @Override
            public void run() {
                if(cpuRunning) {
                    cpuInterrupted = true;
                    handleInfo("Stopped because of breakpoint");
                    int index = breakpoints.indexOf(breakpoint);
                    if(index < 0) breakpointsWatchesTable.getSelectionModel().clearSelection();
                    else breakpointsWatchesTable.getSelectionModel().setSelectionInterval(index, index);
                }
            }
        });
        breakpoints.add(breakpoint);
        updateBreakpoints();
    }
    
    /** Called every time when user have pressed "Remove breakpoint" button. */
    private void doRemoveBreakpoint() {
        int row = breakpointsWatchesTable.getSelectedRow();
        if(row >= 0) {
            Breakpoint16 breakpoint = breakpoints.get(row);
            breakpoint.remove();
            breakpoints.remove(row);
            updateBreakpoints();
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFileChooser_Import = new javax.swing.JFileChooser();
        jFileChooser_Export = new javax.swing.JFileChooser();
        processorPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        programCounterTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        instructionRegisterTextField = new javax.swing.JTextField();
        registersScrollPane = new javax.swing.JScrollPane();
        registersTable = new javax.swing.JTable();
        memoryPanel = new javax.swing.JPanel();
        memoryScrollPane = new javax.swing.JScrollPane();
        memoryTable = new javax.swing.JTable();
        instructionsPanel = new javax.swing.JPanel();
        instructionsScrollPane = new javax.swing.JScrollPane();
        instructionsTable = new javax.swing.JTable();
        ioPanel = new javax.swing.JPanel();
        terminalScrollPane = new javax.swing.JScrollPane();
        terminalTextArea = new javax.swing.JTextArea();
        jLabel3 = new javax.swing.JLabel();
        inputTextField = new javax.swing.JTextField();
        inputLockCheckBox = new javax.swing.JCheckBox();
        breakpointsWatchesPanel = new javax.swing.JPanel();
        breakpointsWatchesScrollPane = new javax.swing.JScrollPane();
        breakpointsWatchesTable = new javax.swing.JTable();
        addBreakpointButton = new javax.swing.JButton();
        removeBreakpointButton = new javax.swing.JButton();
        breakpointTypeComboBox = new javax.swing.JComboBox();
        breakpointMemComboBox = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        breakpointAddressTextField = new javax.swing.JTextField();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem10 = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        jMenu3 = new javax.swing.JMenu();
        jMenuItem6 = new javax.swing.JMenuItem();
        jMenuItem7 = new javax.swing.JMenuItem();
        jMenuItem13 = new javax.swing.JMenuItem();
        jMenu4 = new javax.swing.JMenu();
        jMenuItem8 = new javax.swing.JMenuItem();
        jMenuItem9 = new javax.swing.JMenuItem();
        jMenuItem12 = new javax.swing.JMenuItem();
        jMenuItem14 = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jMenuItem5 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();
        jMenuItem11 = new javax.swing.JMenuItem();

        jFileChooser_Import.setCurrentDirectory(null);

        jFileChooser_Export.setCurrentDirectory(null);
        jFileChooser_Export.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Simple CPU Simulator");

        processorPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Processor", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP));

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("PC");

        programCounterTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                programCounterTextFieldActionPerformed(evt);
            }
        });

        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("IR");

        instructionRegisterTextField.setEditable(false);

        registersTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {"R0", null},
                {"R1", null},
                {"R2", null},
                {"R3", null},
                {"R4", null},
                {"R5", null},
                {"R6", null},
                {"R7", null},
                {"R8", null},
                {"R9", null},
                {"R10", null},
                {"R11", null},
                {"R12", null},
                {"R13", null},
                {"R14", null},
                {"R15", null}
            },
            new String [] {
                "Ri", "Value"
            }
        ));
        registersTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        registersTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        registersScrollPane.setViewportView(registersTable);

        javax.swing.GroupLayout processorPanelLayout = new javax.swing.GroupLayout(processorPanel);
        processorPanel.setLayout(processorPanelLayout);
        processorPanelLayout.setHorizontalGroup(
            processorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, processorPanelLayout.createSequentialGroup()
                .addGroup(processorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 42, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(processorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(programCounterTextField)
                    .addComponent(instructionRegisterTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 96, Short.MAX_VALUE)))
            .addComponent(registersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
        );
        processorPanelLayout.setVerticalGroup(
            processorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(processorPanelLayout.createSequentialGroup()
                .addGroup(processorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(programCounterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(processorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(instructionRegisterTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(registersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 274, Short.MAX_VALUE))
        );

        memoryPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Memory", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP));

        memoryTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Addr", "Value"
            }
        ));
        memoryTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        memoryTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        memoryScrollPane.setViewportView(memoryTable);

        javax.swing.GroupLayout memoryPanelLayout = new javax.swing.GroupLayout(memoryPanel);
        memoryPanel.setLayout(memoryPanelLayout);
        memoryPanelLayout.setHorizontalGroup(
            memoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(memoryScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 151, Short.MAX_VALUE)
        );
        memoryPanelLayout.setVerticalGroup(
            memoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(memoryScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 324, Short.MAX_VALUE)
        );

        instructionsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Instruction editor", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP));

        instructionsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Addr", "Data", "Label", "Instruction", "Good"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.Object.class, java.lang.Object.class, java.lang.Object.class, java.lang.Boolean.class
            };
            boolean[] canEdit = new boolean [] {
                false, true, true, true, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        instructionsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        instructionsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        instructionsScrollPane.setViewportView(instructionsTable);

        javax.swing.GroupLayout instructionsPanelLayout = new javax.swing.GroupLayout(instructionsPanel);
        instructionsPanel.setLayout(instructionsPanelLayout);
        instructionsPanelLayout.setHorizontalGroup(
            instructionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(instructionsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 316, Short.MAX_VALUE)
        );
        instructionsPanelLayout.setVerticalGroup(
            instructionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(instructionsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 324, Short.MAX_VALUE)
        );

        ioPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Input/Output device emulation", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP));

        terminalTextArea.setColumns(20);
        terminalTextArea.setEditable(false);
        terminalTextArea.setRows(5);
        terminalScrollPane.setViewportView(terminalTextArea);

        jLabel3.setText("Data:");

        inputTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inputTextFieldActionPerformed(evt);
            }
        });

        inputLockCheckBox.setSelected(true);
        inputLockCheckBox.setText("Read lock");
        inputLockCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                inputLockCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout ioPanelLayout = new javax.swing.GroupLayout(ioPanel);
        ioPanel.setLayout(ioPanelLayout);
        ioPanelLayout.setHorizontalGroup(
            ioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ioPanelLayout.createSequentialGroup()
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(inputTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 202, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(inputLockCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addComponent(terminalScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 366, Short.MAX_VALUE)
        );
        ioPanelLayout.setVerticalGroup(
            ioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, ioPanelLayout.createSequentialGroup()
                .addComponent(terminalScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 199, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(inputTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(inputLockCheckBox)))
        );

        breakpointsWatchesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Breakpoints&Watches", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP));

        breakpointsWatchesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Address", "Value", "Type", "Target", "Enabled"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.Object.class, java.lang.Object.class, java.lang.Object.class, java.lang.Boolean.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        breakpointsWatchesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        breakpointsWatchesScrollPane.setViewportView(breakpointsWatchesTable);

        addBreakpointButton.setText("Add");
        addBreakpointButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addBreakpointButtonActionPerformed(evt);
            }
        });

        removeBreakpointButton.setText("Remove");
        removeBreakpointButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeBreakpointButtonActionPerformed(evt);
            }
        });

        breakpointTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Read", "Write", "Change" }));

        breakpointMemComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Reg", "Mem", "Code" }));

        jLabel4.setText("Addr:");

        javax.swing.GroupLayout breakpointsWatchesPanelLayout = new javax.swing.GroupLayout(breakpointsWatchesPanel);
        breakpointsWatchesPanel.setLayout(breakpointsWatchesPanelLayout);
        breakpointsWatchesPanelLayout.setHorizontalGroup(
            breakpointsWatchesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(breakpointsWatchesPanelLayout.createSequentialGroup()
                .addComponent(addBreakpointButton, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeBreakpointButton, javax.swing.GroupLayout.DEFAULT_SIZE, 145, Short.MAX_VALUE))
            .addGroup(breakpointsWatchesPanelLayout.createSequentialGroup()
                .addComponent(breakpointTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(breakpointMemComboBox, 0, 69, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(breakpointAddressTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addComponent(breakpointsWatchesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
        );
        breakpointsWatchesPanelLayout.setVerticalGroup(
            breakpointsWatchesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, breakpointsWatchesPanelLayout.createSequentialGroup()
                .addComponent(breakpointsWatchesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 165, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(breakpointsWatchesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(breakpointTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(breakpointMemComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(breakpointAddressTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(breakpointsWatchesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addBreakpointButton)
                    .addComponent(removeBreakpointButton)))
        );

        jMenu1.setText("File");

        jMenuItem10.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem10.setText("New");
        jMenuItem10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem10ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem10);
        jMenu1.add(jSeparator1);

        jMenu3.setText("Import");

        jMenuItem6.setText("Memory data");
        jMenuItem6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem6ActionPerformed(evt);
            }
        });
        jMenu3.add(jMenuItem6);

        jMenuItem7.setText("Asm file");
        jMenuItem7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem7ActionPerformed(evt);
            }
        });
        jMenu3.add(jMenuItem7);

        jMenuItem13.setText("Code binary");
        jMenuItem13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem13ActionPerformed(evt);
            }
        });
        jMenu3.add(jMenuItem13);

        jMenu1.add(jMenu3);

        jMenu4.setText("Export");

        jMenuItem8.setText("Memory data");
        jMenuItem8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem8ActionPerformed(evt);
            }
        });
        jMenu4.add(jMenuItem8);

        jMenuItem9.setText("Asm file");
        jMenuItem9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem9ActionPerformed(evt);
            }
        });
        jMenu4.add(jMenuItem9);

        jMenuItem12.setText("Code binary");
        jMenuItem12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem12ActionPerformed(evt);
            }
        });
        jMenu4.add(jMenuItem12);

        jMenu1.add(jMenu4);

        jMenuItem14.setText("Edit asm source");
        jMenuItem14.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem14ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem14);
        jMenu1.add(jSeparator2);

        jMenuItem5.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_MASK));
        jMenuItem5.setText("Exit");
        jMenuItem5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem5ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem5);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Run");

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem2.setText("Reset");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem2);

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F7, 0));
        jMenuItem1.setText("Step");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem1);

        jMenuItem3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
        jMenuItem3.setText("Run");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem3);

        jMenuItem4.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, 0));
        jMenuItem4.setText("Run to cursor");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem4ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem4);

        jMenuItem11.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
        jMenuItem11.setText("Stop");
        jMenuItem11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem11ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem11);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(processorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(memoryPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(instructionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(ioPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(breakpointsWatchesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(instructionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(memoryPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(processorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ioPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(breakpointsWatchesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void inputLockCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inputLockCheckBoxActionPerformed
        updateInputLock();
    }//GEN-LAST:event_inputLockCheckBoxActionPerformed

    private void inputTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_inputTextFieldActionPerformed
        dataInputed(inputTextField.getText());
    }//GEN-LAST:event_inputTextFieldActionPerformed

    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
        doReset();
    }//GEN-LAST:event_jMenuItem2ActionPerformed

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        doStep();
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    private void programCounterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_programCounterTextFieldActionPerformed
        doChangePC();
    }//GEN-LAST:event_programCounterTextFieldActionPerformed

    private void jMenuItem10ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem10ActionPerformed
        doNew();
    }//GEN-LAST:event_jMenuItem10ActionPerformed

    private void jMenuItem5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem5ActionPerformed
        setVisible(false);
        dispose();
        System.exit(0);
    }//GEN-LAST:event_jMenuItem5ActionPerformed

    private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
        doRun();
    }//GEN-LAST:event_jMenuItem3ActionPerformed

    private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
        doRunToCursor();
    }//GEN-LAST:event_jMenuItem4ActionPerformed

    private void jMenuItem11ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem11ActionPerformed
        doStop();
    }//GEN-LAST:event_jMenuItem11ActionPerformed

    private void jMenuItem6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem6ActionPerformed
        doImportData();
    }//GEN-LAST:event_jMenuItem6ActionPerformed

    private void jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem7ActionPerformed
        doImportAsmCode();
    }//GEN-LAST:event_jMenuItem7ActionPerformed

    private void jMenuItem8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem8ActionPerformed
        doExportData();
    }//GEN-LAST:event_jMenuItem8ActionPerformed

    private void jMenuItem9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem9ActionPerformed
        doExportAsmCode();
    }//GEN-LAST:event_jMenuItem9ActionPerformed

    private void jMenuItem14ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem14ActionPerformed
        doEditAsmSource();
    }//GEN-LAST:event_jMenuItem14ActionPerformed

    private void jMenuItem13ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem13ActionPerformed
        doImportBinaryCode();
    }//GEN-LAST:event_jMenuItem13ActionPerformed

    private void jMenuItem12ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem12ActionPerformed
        doExportBinaryCode();
    }//GEN-LAST:event_jMenuItem12ActionPerformed

    private void removeBreakpointButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeBreakpointButtonActionPerformed
        doRemoveBreakpoint();
    }//GEN-LAST:event_removeBreakpointButtonActionPerformed

    private void addBreakpointButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addBreakpointButtonActionPerformed
        doAddBreakpoint();
    }//GEN-LAST:event_addBreakpointButtonActionPerformed

    /**
     * Main method which will create form and show it. Also it applies Nimbus skin.
     * @param args the command line arguments, it will be ignored
     */
    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addBreakpointButton;
    private javax.swing.JTextField breakpointAddressTextField;
    private javax.swing.JComboBox breakpointMemComboBox;
    private javax.swing.JComboBox breakpointTypeComboBox;
    private javax.swing.JPanel breakpointsWatchesPanel;
    private javax.swing.JScrollPane breakpointsWatchesScrollPane;
    private javax.swing.JTable breakpointsWatchesTable;
    private javax.swing.JCheckBox inputLockCheckBox;
    private javax.swing.JTextField inputTextField;
    private javax.swing.JTextField instructionRegisterTextField;
    private javax.swing.JPanel instructionsPanel;
    private javax.swing.JScrollPane instructionsScrollPane;
    private javax.swing.JTable instructionsTable;
    private javax.swing.JPanel ioPanel;
    private javax.swing.JFileChooser jFileChooser_Export;
    private javax.swing.JFileChooser jFileChooser_Import;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu4;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem10;
    private javax.swing.JMenuItem jMenuItem11;
    private javax.swing.JMenuItem jMenuItem12;
    private javax.swing.JMenuItem jMenuItem13;
    private javax.swing.JMenuItem jMenuItem14;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JMenuItem jMenuItem5;
    private javax.swing.JMenuItem jMenuItem6;
    private javax.swing.JMenuItem jMenuItem7;
    private javax.swing.JMenuItem jMenuItem8;
    private javax.swing.JMenuItem jMenuItem9;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPanel memoryPanel;
    private javax.swing.JScrollPane memoryScrollPane;
    private javax.swing.JTable memoryTable;
    private javax.swing.JPanel processorPanel;
    private javax.swing.JTextField programCounterTextField;
    private javax.swing.JScrollPane registersScrollPane;
    private javax.swing.JTable registersTable;
    private javax.swing.JButton removeBreakpointButton;
    private javax.swing.JScrollPane terminalScrollPane;
    private javax.swing.JTextArea terminalTextArea;
    // End of variables declaration//GEN-END:variables
}
