import subprocess
from openai import OpenAI
import streamlit as st
from pathlib import Path


client = OpenAI()

st.title("Error Examiner")

if "mess" not in st.session_state:
    st.session_state.mess = False



# Wait for the debugging to be requested (path provided)

if "java_process" not in st.session_state:
    st.session_state.output_lines = []
    st.session_state.error_desc = []
    st.session_state.waiting_input = False
    st.session_state.for_user = True
    st.session_state.filepath = ""

    file_path = st.text_input("Please enter the path to your java file:")

    if file_path:

        check_path = Path(file_path)
        if check_path.exists():
            st.write("Information received, please wait for the analysis to be done...")
            moduleCall = ['java', '-jar', 'prototype/target/ErrorExaminer.jar',file_path]
            st.session_state.filepath = file_path
            st.session_state.temp_input = ""
            st.session_state.java_process = subprocess.Popen(
                moduleCall,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1
            )
            print("Starting the error examine with code")
            st.session_state.mess = False
            st.rerun()
        else:
            # case where the file path provided is not valid
            print("File not found")
            st.session_state.mess = True
            st.warning("File not found")
    else:
        st.stop() 




# if path has been provided and rpcess started
if "java_process" in st.session_state:

    p = st.session_state.java_process

    while True:

        if not st.session_state.waiting_input:
            line = p.stdout.readline()
            if not line:
                if p.poll() is not None: 
                    break

            if "[JDI - Input is required]" in line:
                st.session_state.temp_input= ""
                st.session_state.waiting_input = True
                break
            elif "[end of output]" in line:
                st.session_state.for_user = False
            else:
                
                if st.session_state.for_user:
                    st.session_state.output_lines.append(line)
                if not st.session_state.for_user:
                    st.session_state.error_desc.append(line)

        
        if st.session_state.waiting_input:
            break

    outputLines=""

    for line in st.session_state.output_lines:
        outputLines = outputLines + line


    st.markdown(
        f"""
        <div>Running your program from: {st.session_state.filepath}</div>
        <div style="
            background-color: #0b0f0c;
            color: #7CFC98;
            padding: 16px;
            border-radius: 6px;
            font-family: Consolas, 'Courier New', monospace;
            font-size: 14px;
            line-height: 1.4;
            white-space: pre-wrap;
        ">{outputLines}</div>
        """,
        unsafe_allow_html=True
    )


    if st.session_state.waiting_input:

        st.markdown("""
        <style>
            .stTextInput input[aria-label="Provide input:"] {
                background-color: #0b0f0c;
                color: #7CFC98;
            }
        """, unsafe_allow_html=True)


        user_input = st.text_input("Provide input:", key="temp_input")
        if user_input:
            st.session_state.output_lines.append(user_input+"\n")
            input_to_push = user_input + "\n"
            p.stdin.write(input_to_push)
            p.stdin.flush()
            print("Pushed:", user_input, flush=True)
            st.session_state.waiting_input = False
            
            st.rerun()

    # once the debugging module is done providing output
    if p.poll() is not None:
        st.info("Java program finished")
        p.stdin.close()
        p.stdout.close()
        p.stderr.close()
        st.session_state.java_process = None

        errorExaminerOutput = ""

        for line in st.session_state.error_desc:
                errorExaminerOutput = errorExaminerOutput + line + "\n"
        ######


        if "No exception was found" in errorExaminerOutput:
            st.success("No runtime error found")
        else:
            st.warning("An exception was spotted")

            # remove keywprds used to read correctly
            errorExaminerOutput = errorExaminerOutput.replace("INVESTIGATE VM CLOSED","")
            errorExaminerOutput = errorExaminerOutput.replace("ERROR VM CLOSED","")
            errorExaminerOutput = errorExaminerOutput.replace("This error is supported","")

            # send it to OpenAI and get a response
            context = """
            answer the question using information from the debug session provided, by filling in the given template by replacing [fill] snippets (return exactly filled in template, up to line before ending tag). Display information following the Scientific method.
            Don't propose your own solutions, Don't make assumptions about code not provided, Don't provide a solution as code. Keep answers concise.  
            (explain shortly each step). Design the answers (each [fill]) as snippets which can be put in st.markdown(snippet). Ensure code examples have code appearance and that the 
            error line is shown both alone and in code context. Highlight the exact error line only in context using '-' in ```diff ... ``` syntax (ensure no indentation before backticks, show the code context in it). 
            Make sure the code lines are aligned well. Keep normal text as points, inlude instructions on how to make a conditional breakpoint:

            <template>
            step:
            **Observe What Happened**
            [fill]
            step:
            **Formulate a Question**
            [fill]
            step:
            **Formulate a Hypothesis**
            [fill]
            step:
            **Test the Prediction**
            [fill]
            step:
            **Analyse the Results**
            [fill]
            step:
            **Form a Conclusion**
            [fill]
            <end of template>

            debug information:
            """
            context = context + errorExaminerOutput
            #print(context)

            print("sent")

            with st.spinner("Processing, please wait..."):
                # call openAI with the stuff
                model="gpt-5.3",
                response = client.responses.create(
                    model="gpt-5.2",
                    instructions=context,
                    input="Why is my program throwing an exception, how can i go about fixing it?"
                )
                reply_steps = response.output_text.replace("<end of template>", "").split("step:\n")

                # print what AI said
                with st.expander("Observe What Happened"):
                    st.markdown(reply_steps[1])
                with st.expander("Formulate a Question"):
                    st.markdown(reply_steps[2])
                with st.expander("Formulate a Hypothesis"):
                    st.markdown(reply_steps[3])
                with st.expander("Test the Prediction"):
                    st.markdown(reply_steps[4])
                with st.expander("Analyse the Results"):
                    st.markdown(reply_steps[5])
                with st.expander("Form a Conclusion"):
                    st.markdown(reply_steps[6])

            st.success("DONE!")