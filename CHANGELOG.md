#v1.1
* Sort question history by time taken to answer when saving, which allows the file to be manually inspected to see which Qs are weaker.
* Display time spent in this session and number of questions answered in the title bar, so you can decide when to stop.
* Change selection algorithm to prioritize asking questions the user "passed" ahead of newly added (never-asked) questions. 
* Change selection algorithm to focus on newly added (and passed) questions when they make up >15% of the total, since when a lot of new questions are added it doesn't make sense to spend lots of time on improving/refreshing already-known questions. 
* Add plugin API to allow for non-textual questions. Created a demonstration plugin and question file that plays some musical notes as the "question" and gets the user to answer by typing the solfege names for the notes (do/re/me/etc). 
