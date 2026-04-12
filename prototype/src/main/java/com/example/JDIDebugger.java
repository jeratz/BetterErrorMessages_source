package com.example;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.Field;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.Value;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.event.StepEvent;

// Initial JDI usage was learned through the tutorial: Anshul Bansal, "An Intro to the Java Debug
// Interface (JDI)", Baeldung, [Online]. Available: https://www.baeldung.com/java-debug-interface



public class JDIDebugger {

    private int[] breakPointLines;
    private static String className;
    private String classPath;
    private String javaFile;
    private static StepRequest stepRequest = null;
    private static String breakpointMethodName = null; 

    // setters
    public void setClass(String className, String classPath) {
        JDIDebugger.className = className;
        this.classPath = classPath;
    }

    public void setJavaFile(String javaFile) {
        this.javaFile = javaFile;
    }

    public int[] getBreakPointLines() {
        return breakPointLines;
    }
    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }


    //
    // handles launching the JVMs
    //
    public VirtualMachine connectAndLaunchVM() throws Exception {
        
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(className);

        if (!classPath.isEmpty()) {
            Connector.Argument options = arguments.get("options");
            String currentOptions = options.value();
            options.setValue(currentOptions + " -classpath " + classPath);
        }

        return launchingConnector.launch(arguments);
    }

    // enables request
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(className);
        classPrepareRequest.enable();
    }

    // 
    // Retrieves varibales present at the event
    //
    public static Map<LocalVariable, Value> getVariablesAtEvent(LocatableEvent event)
     throws IncompatibleThreadStateException, AbsentInformationException {

        String functionName;
        try {
            functionName = findErrorLineInCode(event.thread()).method().name();
    
            StackFrame stackFrame = event.thread().frame(0);
            ThreadReference thread = event.thread();

            for (com.sun.jdi.StackFrame frame : thread.frames()) {
                Location loc = frame.location();
                String currClassName = loc.declaringType().name();
                String currMethodName = loc.method().name();

                if (currClassName.startsWith(className) && functionName.endsWith(currMethodName)) {
                    stackFrame = frame;
                }
            }

            if(stackFrame.location().toString().contains(className)) {
                Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
                return visibleVariables;
            }

            return null;
        } catch (IOException e) {
            e.printStackTrace();

            return null;
        }
    }


    //
    // Performs the first run to get the error. 
    // Allows for Scanner to still be used
    //
    public static ExceptionEvent runGetError(VirtualMachine vm, String input){

        Scanner sc = new Scanner(System.in);

        try {
            EventRequestManager erm = vm.eventRequestManager();

            //make sure we can get errors
            ExceptionRequest exReq = erm.createExceptionRequest(null,true,true);
            exReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            exReq.enable();

            // Something to allow us to detect that a scanner is waiting
            MethodEntryRequest req = erm.createMethodEntryRequest();
            req.addClassFilter("java.util.Scanner");
            req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            req.enable();

            Process debuggeeProcess = vm.process();
            OutputStream stdin = debuggeeProcess.getOutputStream();

            EventSet eventSet = null;

            boolean allHasCheckConsumed = true;

            // loop stepping through the program
            while ((eventSet = vm.eventQueue().remove()) != null) {

                for (Event event : eventSet) {
                    if (event instanceof ExceptionEvent) {
                        sc.close();
                        return (ExceptionEvent) event;
                    }

                    if (event instanceof MethodEntryEvent m) {
                        // if we encouter a scanner, request input for it
                        if (isScannerRequest(m)){

                            boolean hasCheck = isHasCheck(m);
                            // allow for check if the scanner is closed already
                            Field f = m.thread().frame(0).thisObject().referenceType().fieldByName("closed");
                            BooleanValue v = (BooleanValue) m.thread().frame(0).thisObject().getValue(f);

                            if (!v.booleanValue() && allHasCheckConsumed){
                                // try to print what we have a so far
                                InputStream in = vm.process().getInputStream();
                                if(in.available()>0){
                                    byte[] buffer = new byte[in.available()];
                                    System.out.print(new String(buffer, 0 , in.read(buffer), StandardCharsets.UTF_8));
                                }

                                System.out.println("[JDI - Input is required]");
                                String newInput = sc.nextLine()+"\n";
                                Main.userInput = Main.userInput + newInput;
                                stdin.write(newInput.getBytes(StandardCharsets.UTF_8));
                                stdin.flush();

                                if (hasCheck){
                                    allHasCheckConsumed = false;
                                }
                                // if the scanner input has been taken we await one again
                            } else if (!hasCheck){
                                allHasCheckConsumed = true;
                            }
                        }
                    }
                    vm.resume();
                }
            }
            
        }
        catch (VMDisconnectedException e) {
            //System.out.println("Virtual Machine is disconnected.");
        }
        catch (Exception e){
            System.out.println("Something went wrong");
            System.out.print(e);
        }
        finally {
            InputStream in = vm.process().getInputStream();
            try {
                if(in.available()>0){
                    byte[] buffer = new byte[in.available()];
                    System.out.println(new String(buffer, 0 , in.read(buffer), StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("[end of output]");
        }
        return null;
    }

    //
    // checks if Scanner requires input to be present
    //
    public static boolean isScannerRequest(MethodEntryEvent m){
        if(m.method().name().startsWith("next") || m.method().name().startsWith("hasNext")){
            //now lets check if the call is made by users program
            try {
                if (m.thread().frameCount()<2){
                    return false;
                }
                // check if it got called from users class
                else if (m.thread().frame(1).location().declaringType().name().contains(className))  {
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    //
    // differentiate between checkig input and consuming it
    //
    public static boolean isHasCheck(MethodEntryEvent m){
        if(m.method().name().startsWith("hasNext")){
            //now lets check if the call is made by users program
            try {
                if (m.thread().frameCount()<2){
                    return false;
                }
                // check if it got called from users class
                else if (m.thread().frame(1).location().declaringType().name().contains(className))  {
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    //
    // run allowng for last changes to variables to be identified
    //
    public static HashMap<Map.Entry<LocalVariable, Value>, Integer> getLastLineChange(
        VirtualMachine vm, JDIDebugger debuggerInstance, String javaFile, 
    HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits, Set<String> involvedCalls, ExceptionEvent ev, String input) 
    throws FileNotFoundException{

        try {
            EventRequestManager erm = vm.eventRequestManager();

            //make sure we can get errors
            ExceptionRequest exReq = erm.createExceptionRequest(null,true,true);
            exReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            exReq.enable();

            // dealing with scanner
            Process debuggeeProcess = vm.process();
            // input provided at the beginning has been replaced with input provided during run
            input = Main.userInput;
            OutputStream stdin = debuggeeProcess.getOutputStream();
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
            stdin.flush();

            EventSet eventSet = null;

            while ((eventSet = vm.eventQueue().remove()) != null) {

                for (Event event : eventSet) {
                    if (event instanceof ClassPrepareEvent) {
                        
                        ClassPrepareEvent cpe = (ClassPrepareEvent) event;
                        com.sun.jdi.ReferenceType mainClass = cpe.referenceType();

                        List<com.sun.jdi.Method> allMethods = mainClass.methods();

                        // esnure breakpoints at user created inputs
                        for (com.sun.jdi.Method m : allMethods) {
                            if (!m.isSynthetic() && !m.name().equals("<init>")){
                                List<Location> locations = m.allLineLocations();
                                Location firstLine = locations.get(0);
                                BreakpointRequest bp = vm.eventRequestManager().createBreakpointRequest(firstLine);
                                bp.enable();
                            }
                        }

                    }

                    if (event instanceof BreakpointEvent) {
                        BreakpointEvent be = (BreakpointEvent) event;
                        lastEdits = debuggerInstance.checkForVariables(be, lastEdits, javaFile, involvedCalls, ev);
                        // remake the request at each breakpoint so we are stepping in the right function
                        if (stepRequest != null) {
                            stepRequest.disable();
                        }
                        stepRequest = debuggerInstance.enableStepRequest(vm, be);
                    }

                    if (event instanceof StepEvent) {
                        StepEvent se = (StepEvent) event;
                        lastEdits = debuggerInstance.checkForVariables(se, lastEdits, javaFile, involvedCalls, ev);
                    }

                    // return once exception is spotted
                    if (event instanceof ExceptionEvent) {
                        return lastEdits;  
                    }
                    vm.resume();
                }
            }
        }

        catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        }
        catch (Exception e){
            System.out.println("Something went wrong " + e.getMessage());
        }
        return lastEdits;
    }

    //
    // Allows for step requests
    //
    public StepRequest enableStepRequest(VirtualMachine vm, BreakpointEvent event) {
        StepRequest stp = vm.eventRequestManager().createStepRequest(event.thread(),StepRequest.STEP_LINE,   StepRequest.STEP_OVER);
        stp.enable();
        return stp;
    }

    //
    // Retrieved the error line that happened in the users code
    //
    public static Location findErrorLineInCode(ThreadReference thread) throws IOException{
        try {
            for (com.sun.jdi.StackFrame frame : thread.frames()) {
                Location loc = frame.location();
                String currClassName = loc.declaringType().name();

                // wait to get users class and the line there
                if (currClassName.startsWith(className)) {
                    return frame.location();
                }
            }
        } catch (IncompatibleThreadStateException e) {
            // Thread not suspended, shouldnt happen
        }
        return null;
    }


    //
    // Compare variables to spot last changes
    //
    public HashMap<Map.Entry<LocalVariable, Value>, Integer>
     checkForVariables (LocatableEvent event, HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits, String javaFile, 
     Set<String> involvedCalls, ExceptionEvent ev) throws NumberFormatException, IOException{

        // have a look at current line. if any of the events is edited, update it
        String line = Files.readAllLines(Paths.get(javaFile)).get(Integer.valueOf(event.location().toString().split(":")[1])-1);
        line = makeUsable(line);
        int lineNo = Integer.valueOf(event.location().toString().split(":")[1]);

        try {     
            Statement parsed = StaticJavaParser.parseStatement(line);
            
            // if we are in the correct method, check if we update any vars
            if (findErrorLineInCode(ev.thread()).method().name().equals(event.location().method().name()) 
                && !findErrorLineInCode(ev.thread()).toString().equals(event.location().toString())){

                // handles different encounters
                parsed.findAll(Expression.class).forEach(expr -> {
                    if (expr.isAssignExpr()) {
                        for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : lastEdits.entrySet()) {
                            if (entry.getKey().getKey().name().equals(expr.asAssignExpr().getTarget().toString())){
                                entry.setValue(lineNo);
                            }
                            if (expr.asAssignExpr().getTarget().isArrayAccessExpr()){
                                if (entry.getKey().getKey().name().equals(expr.asAssignExpr().getTarget().asArrayAccessExpr().getName().toString())){
                                    entry.setValue(lineNo);
                                }
                            }
                        }
                    } 
                    else if (expr.isVariableDeclarationExpr()) {

                        for (VariableDeclarator v : expr.asVariableDeclarationExpr().getVariables()){
                            for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : lastEdits.entrySet()) {
                                if (entry.getKey().getKey().name().equals(v.getNameAsString())){
                                    entry.setValue(lineNo);
                                }
                            }
                        }
                    }
                    else if (expr.isUnaryExpr()){

                        for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : lastEdits.entrySet()) {
                            if (entry.getKey().getKey().name().equals(expr.asUnaryExpr().getExpression().toString())){
                                entry.setValue(lineNo);
                            }
                        }  
                    }

                });

                parsed.findAll(MethodCallExpr.class).forEach(expr -> {
                    expr.getScope().ifPresent(scope -> {
                        for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : lastEdits.entrySet()) {
                            if (entry.getKey().getKey().name().equals(scope.toString())){
                                entry.setValue(lineNo);
                            }
                        }
                    });
            });
            }
            // if if its a return, check it (if a call that is returned now is involved)
            else if (involvedCalls.contains(event.location().method().name())){
                
                parsed.findAll(ReturnStmt.class).forEach(ret -> {
                    Main.returnLines.put(event.location().method().name(), lineNo);
                }
                );
            }
        } catch (Exception e) {
            //
        }
        return lastEdits;
    }

    //
    // Ensure that the condition statements can be parsed
    //
    public static String makeUsable(String myline){

        if (myline.trim().startsWith("for") || myline.trim().startsWith("if")){
            if (myline.trim().endsWith(")")){
                myline = myline + " {}";
            }
            if (myline.trim().endsWith("{")){
                myline = myline + "}";
            }
        }
        return myline;
    }
    
}
