# FlashTeacher
FlashTeacher is a desktop application for learning answers to questions, for example language vocabulary, or musical theory knowledge. 

Features:

* Instead of picking questions randomly, questions are selected using an intelligent algorithm that uses your personal learning history to build your knowledge efficiently, with special handling for questions you often answer slowly or get wrong, questions that were not asked for a long time, and prioritization of new questions and questions that you "passed" (could not remember the answer for at all). 
* If you get an answer wrong (or have to pass), you still have to type the correct answer before moving on, to begin to get it embedded in your memory. 
* Has full support for incrementally increasing the corpus of questions as knowledge improves; newly questions are asked repeatedly until they are learned, before moving on to other new questions.
* Includes a score to show performance compared to last time, and a motivational graph showing increase of knowledge over time (factoring in both how well you know the questions and how big the question corpus is).
* Supports right-left-languages and any UTF-8/international character.  
* Has a plugin API allowing for non-textual questions, for example where the question is presented as an audio sequence or a picture. 
* Includes a simple editor for adding new items to a question list. However note that a simple XML file format is used with the intention that the question files are easy to create and edit. 
* Uses Java 8. Includes an executable launcher for Windows. 
* Includes a plugin for ear training using solfege (so/re/me) names, with hand signs, and the ability to play a "do" drone note. 

NB: if you're interested in this program you should also take a look at https://apps.ankiweb.net/ which is a more established and fully-featured 
open source flash cards program (which I discovered several years after creating this), and which uses a more scientific approach to 
question selection based on the Ebbinghaus forgetting curve. However at the time of writing (without significant work on plugin development) 
FlashTeacher is better if you want an out-of-the-box approach to solfege, and I think the graphical tracking of progress with time is 
another nice feature of FlashTeacher. 

# Getting started
You need a Java(R) Runtime Environment installed on your machine (recommended Java 8) to run this application. On Windows, just run FlashTeacher.exe. On other operating run `java -jar flashteacher.jar`. 

There are sample question files for musical key signatures, biblical Hebrew, and dictation of musical melodies (ear training using do/re/me solfege names; this also demonstrates the plugin API). 

To get started, open the application and specify one of these (hint the musical key signatures might be the easiest to guess/look up if you're not an expert on the other topics!) to get a feel for how it works. 

When you're ready to create your own question set, copy one of the XML question files as a starting point, and edit it with a text editor of your choice. 

Once you have a question file setup as you like, you can enter the questions in that file manually with a text editor, or using the FlashTeacher Editor application. 

You will probably want to create separate start menu shortcuts for launching the tool with each of the question files you create.

Hope you enjoy using this tool!

# Question selection algorithm
You may be wondering how this question selection algorithm actually works... 

Well, for each question we keep track of:

* The average **time taken to answer** the question, so that slow answers can be identified. Since long words may take longer to type than short words (especially in a foreign keyboard layout), the time is adjusted down based on the number of characters in the answer (and the average time to type each character overall). In addition, to avoid skewing the stats, questions that take a really long time are assigned a time of 30 seconds. The time is maintained as an exponentially weighted moving average. 
* If a question is **answered incorrectly** it is assigned a nominal time-to-answer of 50 seconds (i.e. worse than the worst "slow" answer). Any number of guesses can be attempted after a wrong answer but only the first affects the stats for the question. 
* For questions the user **passed** (i.e. could not answer with prompting, even with unlimited guesses), we keep track of the number of times it was answered correctly since it was passed. You should then answer a passed question correctly 3 times before we can assume it's been learned. The algorithm's top priority is to fix these questions that the user had to "pass". 
* How long since the question was **last asked**, so we can regularly refresh the users' memory of old questions before anything gets forgotten. 

The algorithm then randomly chooses a question as follows:
* With probability 50% we select a question from a small "prioritized" list containing at most 10 questions that have been **passed** - or else are **newly added** (never yet asked). Passed questions do not leave this prioritized list until they've been answered correctly 3 times in a row, and when they do another question is added in their place. This focus on learning a very small number of questions at a time helps the user to fully fix gaps in their knowledge before moving on to other questions. Newly added questions must be answered correctly just once (not 3 times) to leave the prioritized list. As a special case, when there are a lot (>15%) of newly added (or passed) questions, the algorithm almost exclusively focuses on getting through them before moving on to the later stages. 
* If not picking from the prioritized list then:
    * With probability 75% we select a question at random from the **slowest/wrongest** 20% of questions (where wrong answers are treated as having an especially high time-to-answer). 
    * With probability 25% (or if any of the above are not possible) we select a question at random from the 10% of questions that were **least recently asked**, to ensure nothing gets forgotten. 

# License
Copyright (C) Ben Spiller 2007-2021

MIT License
