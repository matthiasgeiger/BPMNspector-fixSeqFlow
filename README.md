# BPMNspector-fixSeqFlow <img align="right" src="https://github.com/uniba-dsg/BPMNspector/raw/master/src/main/resources/reporting/res/logo-h100.png" height="100" width="217"/>
**Fixing Sequence Flow Issues in BPMN models.**

## Description
Using the BPMN 2.0 process model analysis tool [BPMNspector](https://github.com/uniba-dsg/BPMNspector) revealed 
that one of the most common mistakes in BPMN processes is a violation of a ```<sequenceFlow>``` constraint 
(named EXT.023 in BPMNspector):
```XML
  <process id="P1" name="A Basic Process" isExecutable="true">
    <startEvent id="start" name="Start"></startEvent>
    
    <sequenceFlow id="flow1" sourceRef="start" targetRef="task"></sequenceFlow>
    
    <task id="task" name="Do The Work!"></task>
    
    <sequenceFlow id="flow2" sourceRef="task" targetRef="end"></sequenceFlow>
    
    <endEvent id="end" name="The End."></endEvent>
  </process>
```
Although this process looks perfectly sound, the Standard Document 
(see [here](http://www.omg.org/spec/BPMN/2.0.2/PDF), p. 96-98) requires developers/tools 
to introduce some redundancy:

```XML
  <process id="P1" name="A Basic Process" isExecutable="true">
    <startEvent id="start" name="Start">
      <outgoing>flow1</outgoing>
    </startEvent>
    
    <sequenceFlow id="flow1" sourceRef="start" targetRef="task"></sequenceFlow>
    
    <task id="task" name="Do The Work!">
      <incoming>flow1</incoming>
      <outgoing>flow2</outgoing>
    </task>
    
    <sequenceFlow id="flow2" sourceRef="task" targetRef="end"></sequenceFlow>
    
    <endEvent id="end" name="The End.">
      <incoming>flow2</incoming>
    </endEvent>
  </process>
```

BPMNspector-fixSeqFlow adds the missing ```<incoming>```/```<outgoing>``` elements and creates a corrected
version of a given BPMN process file.

## Requirements
BPMNspector-fixSeqFlow uses gradlew  - therefore, only a Java 8 installation is needed.
Download and configuration of needed libraries is performed on the fly.

 - JDK 1.8.0 (or higher)
    - JAVA_HOME should point to the jdk directory
    - PATH should include JAVA_HOME/bin
    
## Usage

To use BPMNspector-fixSeqFlow simply run the start script:

```
$ BPMNspector-fixSeqFlow fileToFix.bpmn
```
or
```
$ BPMNspector-fixSeqFlow folder/to/fix
```
*Note: All *.bpmn, *.bpmn2 and *.bpmn20.xml in this and all subfolders will be analyzed and fixed.*

If the file contains an error which can be fixed a copy of the file prefixed with ```fixed_``` will be 
created which contains the corrected process.

## Licensing
LGPL Version 3: http://www.gnu.org/licenses/lgpl-3.0.html

## Authors
[Matthias Geiger](http://www.uni-bamberg.de/en/pi/team/geiger-matthias/)

## Further Information
BPMNspector-fixSeqFlow and BPMNspector are part of ongoing research on BPMN process model quality.
For further information visit the project web page at the University of Bamberg:
http://www.uni-bamberg.de/pi/bpmn-constraints
