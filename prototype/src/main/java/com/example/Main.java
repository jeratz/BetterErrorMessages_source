package com.example;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.StackWalker.StackFrame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.Value;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.VirtualMachine;

public class Main {

    // vriables which will be reused at most time
    public static String className = "";
    public static String classPlace = "";
    public static String javaFile = "";
    public static String input = "";
    public static String userInput = "";

    public static VirtualMachine errorVM = null;
    public static VirtualMachine investigateVM = null;

    public static Set<String> supported = new HashSet<>();

    public static Set<String> involvedVars = new HashSet<>();
    public static Set<String> involvedCalls = new HashSet<>();
    public static HashMap<String, String> involvedAccesses = new HashMap<>();

    public static HashMap<String, Set<String>> snippetInfo = new HashMap<>();
    public static HashMap<String, Integer> returnLines = new HashMap<>();



    // 
    // Main Entry Point - triggered by the Python Controller Module
    //
    public static void main(String[] args) throws AbsentInformationException {
        // list of errors supported as of now
        supported.add("java.lang.ArithmeticException");
        supported.add("java.lang.ArrayIndexOutOfBoundsException");
        supported.add("java.lang.NullPointerException");
        supported.add("java.util.InputMismatchException");
        // error casued by closed scanner in introductory modules
        supported.add("java.lang.IllegalStateException");

        //gets the Path provided to program to debug
        javaFile = args[0];

        // Used to Get input, now replaced by taking input as it runs
        if (args.length>1)
        {
            input = args[1].replaceAll("\\\\n", "\n");
        }

        //compile give file
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String[] compileOptions = {"-g",javaFile};
        compiler.run(null, null, null, compileOptions);
        Path filePath = Paths.get(javaFile);
        className = filePath.getFileName().toString().replaceAll(".java", "");
        classPlace = filePath.getParent().toString();

        // prepare for the JDI flow
        JDIDebugger debuggerInstance = new JDIDebugger();
        debuggerInstance.setClass(className, classPlace);
        debuggerInstance.setJavaFile(javaFile);
        try 
        {
            errorVM = debuggerInstance.connectAndLaunchVM();
            debuggerInstance.enableClassPrepareRequest(errorVM);
            // starts the main flow of eploring the error
            getError(input);

        } catch (Exception e) 
        {
            e.printStackTrace();
        }
        finally 
        {
            // ensure the JVMs are closed
            if (errorVM != null) 
                {
                try 
                {
                    errorVM.exit(0);
                } catch (VMDisconnectedException ignored) {
                } catch (Exception ex) 
                {
                    ex.printStackTrace();
                }
                try 
                {
                    errorVM.dispose();
                } catch (Exception ignored) {
                }
            }

            // print once ensured all JVMs have finished
            System.out.println("ERROR VM CLOSED");
        }
    }

    //
    // Function starts the run  to retrieve the error
    // Then triggers following fucntions
    //
    public static void getError(String input) throws AbsentInformationException{

        ExceptionEvent event = JDIDebugger.runGetError(errorVM, input);

        if (event == null)
        {
            System.out.println("No exception was found");
        }
        else
        {
            try {
                tryGetException(event, javaFile);
            } catch (NumberFormatException | IncompatibleThreadStateException | IOException e) {
                e.printStackTrace();
            }
        }

    }

    //
    // Begins retrieved error event examination
    //
    public static void tryGetException(ExceptionEvent event, String javaFile)
     throws IncompatibleThreadStateException, NumberFormatException, IOException, AbsentInformationException {

        ObjectReference exception = event.exception();
        System.out.println("Exception thrown: " + exception.referenceType().name() + " at line " +event.location().toString());

        if (supported.contains(exception.referenceType().name())) {

            JDIDebugger debuggerInstance = new JDIDebugger();
            debuggerInstance.setClass(className, classPlace);
            debuggerInstance.setJavaFile(javaFile);

            try {

                // Error was found, hence investigation JVM is triggered
                investigateVM = debuggerInstance.connectAndLaunchVM();
                debuggerInstance.enableClassPrepareRequest(investigateVM);
                System.out.println("\n1. Error inspection results:");

                // call to the appropriate function for the exception found
                if (exception.referenceType().name().equals("java.lang.ArithmeticException")){
                    handleArithmeticException(event, javaFile, debuggerInstance);
                }
                else if (exception.referenceType().name().equals("java.lang.ArrayIndexOutOfBoundsException")){
                    handleArrayIndexOutOfBoundsException(event, javaFile, debuggerInstance);
                }
                else if (exception.referenceType().name().equals("java.lang.NullPointerException")){
                    handleNullPointerException(event, javaFile, debuggerInstance);
                }
                else if (exception.referenceType().name().equals("java.util.InputMismatchException")){
                    handleInputMismatchException(event, javaFile, debuggerInstance);
                }
                else if (exception.referenceType().name().equals("java.lang.IllegalStateException")){
                    handdleIllegalStateException(event, javaFile, debuggerInstance);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {

                // ensure the investigation JVM is closed in the end
                if (investigateVM != null) {
                    try {
                        investigateVM.exit(0);
                    } catch (VMDisconnectedException ignored) {
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    try {
                        investigateVM.dispose();
                    } catch (Exception ignored) {
                    }
                }
                System.out.println("INVESTIGATE VM CLOSED");
            }
        } else {
            // exception spotted is not on the supported list
            System.out.println("This error is not supported");
        }
    }




    /**
     * 
     * =============================================
     *     FUNCTIONS HANDLING SHOWING CONTEXT
     * =============================================
     */


    //  
    // Show the context of the error - surrounding
    //
    public static void printContext(ExceptionEvent event,  String javaFile) throws NumberFormatException, IOException{

        // retrieve the error line
        String errorLine = Files.readAllLines(Paths.get(javaFile)).get(Integer.valueOf(event.location().toString().split(":")[1])-1);
        System.out.println("The line is: "+errorLine);
        System.out.println("In code context: <start>");

        int lineNo = (Integer.valueOf(event.location().toString().split(":")[1])-1);
        int start = lineNo-3 >= 0 ? lineNo-3 : 0;
        int end = lineNo+3 < Files.readAllLines(Paths.get(javaFile)).size() ? lineNo+3 : Files.readAllLines(Paths.get(javaFile)).size()-1;

        // prepare context
        for (; start<=end; start++) {
            System.out.print(Files.readAllLines(Paths.get(javaFile)).get(start));
            if (start==lineNo) {
                System.out.print(" <--- error line");
            }
            System.out.println();
        }
        System.out.println("<end>\n");

    }

    //
    // handles finding context for user code when error is thrown outside of it
    //
    public static String findErrorLineInCode(ThreadReference thread) throws IOException{
        try {
            for (com.sun.jdi.StackFrame frame : thread.frames()) {
                Location loc = frame.location();
                String currClassName = loc.declaringType().name();

                // wait to get users class and the line there
                if (currClassName.startsWith(className)) {
                    String errorLine = Files.readAllLines(Paths.get(javaFile)).get(loc.lineNumber()-1);
                    System.out.println("The line is: "+errorLine + " at line " + loc.lineNumber());

                    System.out.println("In code context: <start>");
                    int lineNo = loc.lineNumber()-1;
                    int start = lineNo-3 >= 0 ? lineNo-3 : 0;
                    int end = lineNo+3 < Files.readAllLines(Paths.get(javaFile)).size() ? lineNo+3 : Files.readAllLines(Paths.get(javaFile)).size()-1;

                    for (; start<=end; start++){
                        System.out.print(Files.readAllLines(Paths.get(javaFile)).get(start));
                        if (start==lineNo){
                            System.out.println(" <--- error line");
                        }
                        System.out.println();
                    }
                    System.out.println("<end>\n");



                    return errorLine;
                }
            }
        } catch (IncompatibleThreadStateException e) {
            // Thread not suspended, shouldnt happen
        }
        return null;
    }



    /**
     * 
     * =============================================
     *     FUNCTIONS HANDLING CASES PER EXCEPTION
     * =============================================
     */


    //
    // function taking care of discovering error details as well as presenting information for arithmetic exception
    //
    public static void handleArithmeticException(ExceptionEvent event, String javaFile, JDIDebugger debuggerInstance)
     throws NumberFormatException, IOException, IncompatibleThreadStateException, AbsentInformationException{

        System.out.println("This error means a division by 0 has happened, which is not allowed. It can be caused by either / or %");

        // context call appropriate to the error found
        printContext(event, javaFile);

        String errorLine = Files.readAllLines(Paths.get(javaFile)).get(Integer.valueOf(event.location().toString().split(":")[1])-1);

        ArrayList<String> suspiciousSnippets = findSnippetsArithmetic(errorLine);
        // collect info on vars, accesses and method calls found at error
        for (String s : suspiciousSnippets) {
            findElements(s);
        }

        // get actual involved vars
        Set<Map.Entry<LocalVariable, Value>> involvedRealVars = findInvolvedVars(errorLine, event);
        HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits = findLastEdit(involvedRealVars, debuggerInstance, event);
        displayInformation(lastEdits, event);
        proposeConditionalArithmetic(lastEdits, event);
    }

    //
    // handles examining IndexOutOfBoundsException
    //
    public static void handleArrayIndexOutOfBoundsException(ExceptionEvent event, String javaFile, JDIDebugger debuggerInstance) 
    throws NumberFormatException, IOException, IncompatibleThreadStateException, AbsentInformationException{

        System.out.println("This error means the program attempted to access array at an index that does not exist");

        String errorLine = Files.readAllLines(Paths.get(javaFile)).get(Integer.valueOf(event.location().toString().split(":")[1])-1);

        printContext(event, javaFile);

        ArrayList<String> suspiciousSnippets = findSnippetsIndex(errorLine);
        // collect info on vars, accesses and method calls found at error
        for (String s : suspiciousSnippets) {
            findElements(s);
        }

        // get actual involved vars
        Set<Map.Entry<LocalVariable, Value>> involvedRealVars = findInvolvedVars(errorLine, event);
        HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits = findLastEdit(involvedRealVars, debuggerInstance, event);
        displayInformation(lastEdits, event);
        proposeConditionalIndex(lastEdits, event);
    }

    //
    // handles NullPointerException
    //
    public static void handleNullPointerException(ExceptionEvent event, String javaFile, JDIDebugger debuggerInstance)
     throws NumberFormatException, IOException, IncompatibleThreadStateException, AbsentInformationException{

        System.out.println("This error means the program attempted use an object, which does not have a value assigned to it");

        String errorLine = Files.readAllLines(Paths.get(javaFile)).get(Integer.valueOf(event.location().toString().split(":")[1])-1);
        printContext(event, javaFile);
        
        ArrayList<String> suspiciousSnippets = findSnippetsNull(errorLine);
        // collect info on vars, accesses and method calls found at error
        for (String s : suspiciousSnippets) {
            findElements(s);
        }

        Set<Map.Entry<LocalVariable, Value>> involvedRealVars = findInvolvedVars(errorLine, event);
        involvedRealVars = findNullVars(involvedRealVars); // if none found, look into array accesses and return stmsts
        HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits = findLastEdit(involvedRealVars, debuggerInstance, event);
        displayInformation(lastEdits, event);
    }

    //
    // handles InputMismatchException
    //
    public static void handleInputMismatchException(ExceptionEvent event, String javaFile, JDIDebugger debuggerInstance) throws NumberFormatException, IOException, IncompatibleThreadStateException, AbsentInformationException{
        System.out.println("This error means the Scanner has a different value in it than the one expected");

        //acquiring the real location
        ThreadReference thread = event.thread();
        String errorLine = findErrorLineInCode(thread);

        ArrayList<String> suspiciousSnippets = findSnippetsScanner(errorLine);
        //System.out.println(suspiciousSnippets);
        for (String s : suspiciousSnippets) {
            findElements(s);
        }
        Set<Map.Entry<LocalVariable, Value>> involvedRealVars = findInvolvedVars(errorLine, event);
        
        for (Map.Entry<LocalVariable,Value> entry : involvedRealVars) {
            if(!entry.getKey().typeName().equalsIgnoreCase("java.util.Scanner")){
                involvedRealVars.remove(entry);
            }
            System.out.println("Involved var found: " + entry.getKey().name());
        }

        System.out.println("If sure the value provide at the time is of correct type, it could mean that the previous one is still in scanner");

    }

    //
    // handles IllegalStateException
    //
    public static void handdleIllegalStateException(ExceptionEvent event, String javaFile, JDIDebugger debuggerInstance) 
    throws IOException, IncompatibleThreadStateException, AbsentInformationException{

        System.out.println("This error means the code attemped to use a scanner after it was already closed");

        //acquiring the real location
        ThreadReference thread = event.thread();
        String errorLine = findErrorLineInCode(thread);
        ArrayList<String> suspiciousSnippets = findSnippetsScanner(errorLine);
        //System.out.println(suspiciousSnippets);
        for (String s : suspiciousSnippets) {
            findElements(s);
        }
        Set<Map.Entry<LocalVariable, Value>> involvedRealVars = findInvolvedVars(errorLine, event);

        for (Map.Entry<LocalVariable,Value> entry : involvedRealVars) {
            if(!entry.getKey().typeName().equalsIgnoreCase("java.util.Scanner")){
                involvedRealVars.remove(entry);
            }
        }

        HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits = findLastEdit(involvedRealVars, debuggerInstance, event);
        displayInformation(lastEdits, event);

    }
    

    /**
     * 
     * =======================================================
     *     FUNCTIONS HANDLING DISPLAYING INFORMATION GATHERED
     * =======================================================
     */


    //
    // Displays information common for exceptions
    //
    public static void displayInformation(HashMap<Map.Entry<LocalVariable, Value>, Integer> foundLocations, ExceptionEvent event) 
    throws IOException{

        // inform about last changes
        System.out.println("\n2. Last changes of involved variables:");

        for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : foundLocations.entrySet()) {
            if (entry.getValue()==0){
                if (entry.getKey().getKey().isArgument()){
                    System.out.println( entry.getKey().getKey().name()  + " is an argument to its function, " + event.location().method().name());
                }
            }
            else {
            System.out.println("Suspicious variable " + entry.getKey().getKey().name() + " was last successfully changed at line " + entry.getValue() + 
        " as \"" + Files.readAllLines(Paths.get(javaFile)).get(entry.getValue()-1).trim() + "\"");
            }
        }

        // special cases
        for (Map.Entry<String, Integer> entry : returnLines.entrySet()){
            System.out.println("A method call (to method " + entry.getKey() + ") involded in error line, has a return at line " + entry.getValue());
        }

        if (foundLocations.entrySet().isEmpty() && returnLines.entrySet().isEmpty()){
            System.out.println("No variables or calls involved have been found. The issue is likely the line assignment itself.");
        }

        // information on simple breakpoint ideas
        System.out.println("\n3. Breakpoint proposals:");
        System.out.println("A breakpoint at suspicious variables last change line.\nproposed: ");

        for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : foundLocations.entrySet()) {
            if (entry.getValue()!=0){
                String varname =  entry.getKey().getKey().name();
                System.out.println( "at line " + entry.getValue() + " to investigate variable " + varname 
                + " and find out what was done to it. Once you step from that line, you see the change happen.");
            }
        }

        for (Map.Entry<String, Integer> entry : returnLines.entrySet()){
            System.out.println("At line " + entry.getValue() + " where method call (" + entry.getKey() + ") involded in error line, has a return");
        }

        if (foundLocations.entrySet().isEmpty() && returnLines.entrySet().isEmpty()){
            System.out.println("No breakpoints proposed. Assignemnt of variable is likely the issue.");
        }
    }

    //
    // dispaly cond breakpoint ideas for Arithmetic Exception
    //
    public static void proposeConditionalArithmetic(HashMap<Map.Entry<LocalVariable, Value>, Integer> foundLocations, ExceptionEvent event){
        System.out.println("\nOption: set conditonal breakpoints to find out when you create a divisor equal to 0.");
        // print where its the last time a var is 
        for (Map.Entry<String, Set<String>> e : snippetInfo.entrySet()) {

            String key = e.getKey();
            Set<String> valueSet = e.getValue();
            int max = 0;

            for (String v : valueSet){
                for (Map.Entry<Map.Entry<LocalVariable, Value>, Integer> entry : foundLocations.entrySet()) {
                    if (entry.getKey().getKey().name().equals(v)){
                        if (entry.getValue()>max){
                            max = entry.getValue();
                        }
                    }
                }
            }

            if (max != 0 ){
            System.out.println(key + "==0   <-- right AFTER line " + max + " ,  to check if evaluate to 0"); 
            }
            else{
                System.out.println(key + "==0   <-- at location where last of values is being updated, to check if evaluate to 0"); 
            }
        }

        // helps LLM make an instruction on making the breakpoint
        System.out.println("\nTip: To make a conditional breakpoint in VSCode, right-click next to the line (one right after the change line) and choose the option");
    }

    //
    // dispaly cond breakpoint ideas for IndexOutOfBounds Exception
    //
    public static void proposeConditionalIndex(HashMap<Map.Entry<LocalVariable, Value>, Integer> foundLocations, ExceptionEvent event) 
    throws IncompatibleThreadStateException, AbsentInformationException{

        System.out.println("\nOption: set conditonal breakpoints to find out when you create an index inappropriate for array");

        Map<LocalVariable, Value> values = JDIDebugger.getVariablesAtEvent(event);
        for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {

            if (entry.getValue() instanceof ArrayReference){
                int length = ((ArrayReference) entry.getValue()).length();

                for (Map.Entry<String,String> entr : involvedAccesses.entrySet()) {

                    if (entr.getKey().contains(entry.getKey().name()+"[")){
                        System.out.println("At the time of error, array " + entry.getKey().name() + " has length of " + length + "." 
                    + " The index " + entr.getValue() + " is incorrect when condition:");
                        System.out.println(entr.getValue() + " < 0 and " + entr.getValue() + " >= " + length);
                        System.out.println("If condition set right after the last index changes triggers, index will cause an error.");
                    }
                }

            }
        }
        System.out.println("\nTip: To make a conditional breakpoint in VSCode, right-click next to the line (one right after the change line) and choose the option");
    }


    /**
     * 
     * =======================================================
     *     FUNCTIONS HANDLING SNIPPETS AND VARIABLES
     * =======================================================
     */

    //
    // retieves real variables from JVM and matches them to ones found
    //
    public static Set<Map.Entry<LocalVariable, Value>> findInvolvedVars(String errorLine, ExceptionEvent event)
     throws IncompatibleThreadStateException, AbsentInformationException{

        Set<Map.Entry<LocalVariable, Value>> involvedRealVars = new HashSet<>();
        Map<LocalVariable, Value> values = JDIDebugger.getVariablesAtEvent(event);
        
        for (Map.Entry<LocalVariable, Value> entry : values.entrySet()) {
            if (involvedVars.contains(entry.getKey().name())){
                System.out.println("Variable found to be involved in error line: " 
                + entry.getKey().typeName()+ " " + entry.getKey().name() + ". At the error equal to " + entry.getValue());
                involvedRealVars.add(entry);
            }
        }
        return involvedRealVars;
    }

    //
    // Handles finding suspicious snippets for division by zero
    //
    public static ArrayList<String> findSnippetsArithmetic(String errorLine){

        errorLine = makeUsable(errorLine);
        ArrayList<String> snippets = new ArrayList<>();
        Statement parsed = StaticJavaParser.parseStatement(errorLine);

        parsed.findAll(Expression.class).forEach(expr -> {

            if (expr.isBinaryExpr()){

                if (expr.asBinaryExpr().getOperator() == BinaryExpr.Operator.DIVIDE){
                    snippets.add(expr.asBinaryExpr().getRight().toString());
                    System.out.println("Found an issue if evaluated to 0: " + expr.asBinaryExpr().getRight().toString());
                }

                if (expr.asBinaryExpr().getOperator() == BinaryExpr.Operator.REMAINDER){
                    snippets.add(expr.asBinaryExpr().getRight().toString());
                    System.out.println("Found an issue if evaluated to 0: " + expr.asBinaryExpr().getRight().toString());
                }
                
            }
        });

        return snippets;
    }

    //
    // finds snippets related to the Scanner errors
    //
    public static ArrayList<String> findSnippetsScanner(String errorLine){

        // ensure can be parsed
        errorLine = makeUsable(errorLine);

        ArrayList<String> snippets = new ArrayList<>();
        Statement parsed = StaticJavaParser.parseStatement(errorLine);
        List<String> methods = Arrays.asList("nextLine", "next", "nextInt", "nextLong", "nextDouble",
            "hasNext", "hasNextInt", "hasNextLine", "close");

        parsed.findAll(MethodCallExpr.class).forEach(expr -> {

            if(methods.contains( expr.getNameAsString())){
                expr.getScope().ifPresent(scope -> {snippets.add(scope+"."+expr.getNameAsString()+"()");});
            }

        });

        return snippets;
    }

    //
    // Indentify null variables
    //
    public static Set<Map.Entry<LocalVariable, Value>> findNullVars (Set<Map.Entry<LocalVariable, Value>> involvedRealVars){
        Set<Map.Entry<LocalVariable, Value>> nullVars = new HashSet<>();

        for (Map.Entry<LocalVariable, Value> entry : involvedRealVars) {
                if (entry.getValue()==null){
                    nullVars.add(entry);
                }
        }
        return nullVars;
    }

    //
    // find snippets related to Index out of bounds exception
    //
    public static  ArrayList<String> findSnippetsIndex (String errorLine){

        errorLine = makeUsable(errorLine);
        ArrayList<String> snippets = new ArrayList<>();
        Statement parsed = StaticJavaParser.parseStatement(errorLine);

        // find array accesses and variables in them
        parsed.findAll(ArrayAccessExpr.class).forEach(access -> {
           snippets.add(access.getName().toString() + "[" + access.getIndex() + "]");
        });

        return snippets;
    }

    //
    // find snippets related to the Null exception (any expression is suspicious)
    //
    public static ArrayList<String> findSnippetsNull (String errorLine){

        errorLine = makeUsable(errorLine);
        ArrayList<String> snippets = new ArrayList<>();
        Statement parsed = StaticJavaParser.parseStatement(errorLine);

        parsed.findAll(Expression.class).forEach(expr -> {
            snippets.add(expr.toString());
        });
        return snippets;

    }

    //
    // finds last edits with the use of JDI
    //
    public static HashMap<Map.Entry<LocalVariable, Value>, Integer> 
    findLastEdit(Set<Map.Entry<LocalVariable, Value>> suspiciousVariables, JDIDebugger debuggerInstance, ExceptionEvent ev) 
    throws FileNotFoundException{

        HashMap<Map.Entry<LocalVariable, Value>, Integer> lastEdits = new HashMap<>();
        for (Map.Entry<LocalVariable, Value> entry : suspiciousVariables) {
            lastEdits.put(entry, 0);
        }
        lastEdits = JDIDebugger.getLastLineChange(investigateVM, debuggerInstance, javaFile, lastEdits, involvedCalls, ev, input);

        return lastEdits;
    }

    //
    // find all elements to consider per snippet
    //
    public static void findElements(String snippet){

        snippetInfo.put(snippet, new HashSet<>());

        try {
       
            Expression expr = StaticJavaParser.parseExpression(snippet);
            expr.findAll(ArrayAccessExpr.class).forEach(access -> {
                involvedAccesses.put(access.getName().toString() + "[" + access.getIndex() + "]", access.getIndex().toString());
            });
            
            expr.findAll(MethodCallExpr.class).forEach(call -> {
                involvedCalls.add(call.getName().toString());
                snippetInfo.get(snippet).add(call.getName().toString());
            });
            
            expr.findAll(NameExpr.class).forEach(var -> {
                involvedVars.add(var.getName().toString());
                snippetInfo.get(snippet).add(var.getName().toString());
            });

         } catch (Exception e) {
           // System.out.println("non expression statement");
        }

    }

    //
    // ensure conditions can be parsed
    //
    public static String makeUsable(String myline){

        myline = myline.split("//")[0];

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
