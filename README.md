# Better Error Messages For Novices

This repository contains a prototype of a tool, which could be used to aid novices understand runtime errors they encouter and learn a structured approach to debugging them.

## Prerequisites 

- Java
- Python
- Python libraries: openai, streamlit
- environment variable OPENAI_API_KEY (OpenAI key)


## commands

### Open the Tool Frontend

When in this directory

```
python -m streamlit run pythonBrain.py          
```

then visit http://localhost:8501/

### to run just the debugging jar

(jar provided is a fat jar already containing all dependencies, should work even without maven installed)

Example commands
```
java -jar prototype/target/ErrorExaminer.jar demoPrograms\Division.java
```
```
java -jar prototype/target/ErrorExaminer.jar demoPrograms\ArrayMistake.java
```