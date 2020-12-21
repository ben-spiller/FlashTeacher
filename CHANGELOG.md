#v1.1
* Add a new plugin API to allow for non-textual questions, for example based on recognition of images or sounds. Created a demonstration plugin and question file that plays some musical notes as the "question" and gets the user to answer by typing the solfege names for the notes (do/re/me/etc). 
* Display the time spent in the current session and number of questions answered in the title bar, so you can decide when you've done enough.
* Change selection algorithm to prioritize asking questions the user "passed" ahead of newly added (never-asked) questions. 
* Change selection algorithm to focus on newly added (and passed) questions when they make up >15% of the total, since when a lot of new questions are added it doesn't make sense to spend lots of time on improving/refreshing already-known questions. 
* Sort question history by time taken to answer when saving, which allows the file to be manually inspected to see which Qs are weaker.
* Question history is now retained on disk when a question is deleted from the question file (though not in the performance status windows), which allows for removing some questions and then re-adding them later without losing their history. This is especially useful when using a plugin to dynamically generate a question set, and experimenting with different parameters. 
