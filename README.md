# FlashTeacher
FlashTeacher is a desktop application for learning answers to questions, for example language vocabulary, or musical theory knowledge. 

Features:

* Instead of picking questions randomly, questions are selected using an intelligent algorithm that uses your personal learning history to build your knowledge efficiently, with special handling for questions you often answer slowly or get wrong, questions that were not asked for a long time, and prioritization of new questions and questions that you "passed" (could not remember the answer for at all). 
* If you get an answer wrong (or have to pass), you still have to type the correct answer before moving on, to begin to get it embedded in your memory. 
* Has full support for incrementally increasing the corpus of questions as knowledge improves; newly questions are asked repeatedly until they are learned, before moving on to other new questions.
* Includes a score to show performance compared to last time, and a motivating graph showing increase of knowledge over time (factoring in both how well you know the questions and how big the question corpus is).
* Supports right-left-languages and any UTF-8/international character.  
* Has a plugin API allowing for non-textual questions, for example where the question is presented as an audio sequence or a picture. 
* Uses Java 8. Includes an executable launcher for Windows. 

There are sample question files for musical key signatures, biblical Hebrew, and dictation of musical melodies (ear training using do/re/me solfege names - with a plugin). 

# Question selection algorithm
How does the question selection algorithm work? Well for each question we keep track of:

* The average **time taken to answer** the question, so that slow answers can be identified. Since long words may take longer to type than short words (especially in a foreign keyboard layout), the time is adjusted down based on the number of characters in the answer (and the average time to type each character overall). In addition, to avoid skewing the stats, questions that take a really long time are assigned a time of 30 seconds. The time is maintained as an exponentially weighted moving average. 
* If a question is **answered incorrectly** it is assigned a nominal time-to-answer of 50 seconds (i.e. worse than the worst "slow" answer). Any number of guesses can be attempted after a wrong answer but only the first affects the stats for the question. 
* For questions the user **passed** (i.e. could not answer with prompting, even with unlimited guesses), we keep track of the number of times it was answered correctly since it was passed. You should then answer a passed question correctly 3 times before we can assume it's been learned. The algorithm's top priority is to fix these questions that the user had to "pass". 
* How long since the question was **last asked**, so we can regularly refresh the users' memory of old questions before anything gets forgotten. 

The algorithm takes this approach to randomly choosing a question:
* With probability 50% we select a question from a small "prioritized" list containing at most 10 questions that have been **passed** - or are newly added/never yet asked. Questions do not leave this prioritized list until they've been answered correctly 3 times in a row, and when they do another question is added in their place. This focus on a very small number of questions helps the user to fix gaps in knowledge before moving on to other questions. 
* With probability 30% we select a question at random from the **slowest/wrongest** 20% of questions (where wrong answers are treated as having an especially high time-to-answer). 
* Finally, with probability 20% we select a question at random from the 10% of questions that were **least recently asked**, to ensure nothing gets forgotten. 

# License
Copyright (C) Ben Spiller 2007-2020
MIT License
