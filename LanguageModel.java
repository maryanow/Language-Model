/* 
 * LanguageModel.java
 *
 * Implements methods for training a language model from a text file,
 * writing a vocabulary, and completes sentences based on training.
 * 
 * Evan Ricks & Iain Maryanow
 * January 2014
 *
 */

import java.util.*;
import java.io.*;

public class LanguageModel {
    HashMap<String,Double> p;         // maps ngrams to conditional probabilities
    ArrayList<String> vocab;          // stores the unique words in the input text
    int maxOrder;                     // maximum n-gram order to compute
    java.util.Random generator;       // random number generator object

    /*  - textFilename is the name of a plaintext training file
    *   - maxOrder is the maximum n-gram order for which to estimate counts
    *   - vocabFilename is the name where the vocab file will be written
    *        vocabFilename can also be null
    *   - countsFilename is the name where the counts will be written
    *        countsFilename can also be null
    *
    *   - this.p maps ngrams (h,w) to the the maximum likelihood estimates 
    *        of P(w|h) for all n-grams up to maxOrder 
    *   - this.vocab contains each word appearing in textFilename exactly once
    *        in the order they appear in textFilename
    */
    
   public LanguageModel(String textFilename, int maxOrder, java.util.Random generator, String vocabFile, String countsFile) {
      this.maxOrder = maxOrder;
      this.generator = generator;
      HashMap<String,Integer> ngramCounts = new HashMap<String,Integer>();
      HashMap<String,Integer> historyCounts = new HashMap<String,Integer>();
      p = new HashMap<String,Double>(); // probabilities
      vocab = new ArrayList<String>();        

      try(Scanner input = new Scanner(new File(textFilename))) { // Checks for valid source text file
         getCounts(input, ngramCounts, historyCounts, vocab, maxOrder);
      }
      catch(FileNotFoundException ex) {
         System.out.println("Error: Unable to open file " + textFilename);
         System.exit(1);
      }

      saveVocab(vocabFile); // Will print each word from vocab to the file given if it is not null
      saveCounts(countsFile, ngramCounts); // Will print each key, a tab, and then the key's value to the file given if it is not null
      convertCountsToProbabilities(ngramCounts, historyCounts);
   }

   public int getMaxOrder() {
      return maxOrder;
   }

    /*  - Until </s> or <fail> is drawn:
    *     1) Draw a new word w according to P(w|h)
    *     2) Print a space and then w
    *     3) w is added to the history h
    *  - Once </s> or <fail> is reached, print that token and then a newline
    *  - Call randomNextWord to draw each new word
    */

   public void randomCompletion(ArrayList<String> history, int order) {
      boolean flag = true;
      ArrayList<String> tempHistory = new ArrayList<String>(history);
      String wordDrawn; 
      
      while (flag) {
         wordDrawn = randomNextWord(tempHistory, order); // Stores the word that will be drawn from randomNextWord given history and order.
         if (wordDrawn.equals("</s>") || wordDrawn.equals("<fail>")) { // Stops while loop when end of sentence or fail is reached.
            flag = false; 
         }
         System.out.print(" " + wordDrawn);
         tempHistory.add(wordDrawn); // Adds drawn word to the end of the temporary history array list.
      }
      
      System.out.println();
   }

   private void saveVocab(String vocabFilename) { // If a text file is provided in the command line arguments, will write all vocab words from the arraylist to the file, line by line
      if(vocabFilename != null) { 
         try {
            PrintWriter toFile = new PrintWriter(vocabFilename);

            for (int i = 0; i < vocab.size(); i++) {
               toFile.println(vocab.get(i));
            }

            toFile.close();
         }
         catch(FileNotFoundException ex) { 
            System.out.println("Error: Unable to open file " + vocabFilename);
            System.exit(1);
         }
      }
   }

   private void saveCounts(String countsFilename, HashMap<String,Integer> ngramCounts) { //if a text file is provided in the command line arguments, will write all ngrams line by line with a tab and then the number of times that they appear in the source text
      if (countsFilename != null) {
         try { 
            PrintWriter toFile = new PrintWriter(countsFilename);

            for(Map.Entry<String,Integer> map : ngramCounts.entrySet()) {
               toFile.println(map.getKey() + "\t" + map.getValue());
            }

            toFile.close();
         }
         catch(FileNotFoundException ex) {
            System.out.println("Error: Unable to open file " + countsFilename);
            System.exit(1);
         }
      }
   }

    //  - Order is the order of n-gram to use 
    //      (i.e. no more than n-1 history words)
    //  - If no word follows that history for the specified order, return "<fail>"
   private String randomNextWord(ArrayList<String> history, int order) { // Draws a random next word based on a random number generator given a history and order
      double randomNum = this.generator.nextDouble();
      double cumulativeSum = 0;
      String key = this.arrayToString(history); 

      if (history.size() >= order) { // Trims history if greater than order size - 1
         key = this.arrayToString(history.subList(history.size() - order + 1, history.size()));
      }

      for (int i = 0; i < vocab.size() - 1; i++) { // Adds probabilities of all vocab words happening after the history
         if (this.p.get(key + " " + vocab.get(i)) != null) {
            cumulativeSum += this.p.get(key + " " + vocab.get(i));
         }

         if (cumulativeSum > randomNum) { // Returns the current vocab word when the probabilities becomes greater than the random number
            return vocab.get(i);     
         }
      }

      if (cumulativeSum == 0) { // Returns <fail> if no words ever follow the given history
         return "<fail>";
      }
      
      return vocab.get(vocab.size() - 1); // Returns last vocab word in the arraylist if the cumulative probabilities never reach the random number generated (for example when the random number generated is or is very close to 1)
   } 

    //  - ngramCounts.get(ngram) contains the number of times that ngram appears in the input
    //      ngram must be 2+ words long (e.g. "<s> i")
    //  - historyCounts.get(history) contains the number of times that ngram history appears in the input
    //      histories can be a single word (e.g. "<s>")

   private void getCounts(java.util.Scanner input, HashMap<String,Integer> ngramCounts, HashMap<String,Integer> historyCounts, 
                          ArrayList<String> vocab, int maxOrder) {
      String line;
      HashSet<String> tempVocab = new HashSet<String>(); // Create a hash set to pass the ngrams through to check for duplicates

      while (input.hasNextLine()) {
         line = input.nextLine();
         int lineLength = line.split(" ").length;
         String phrase = "";
         int lineCounter = 0;
         int phraseCounter = 0;

         while (lineCounter < lineLength - 1) {
            if (phraseCounter >= maxOrder || phraseCounter + lineCounter >= lineLength) { //increments lineCounter if the ngram length becomes greater than the maxOrder or extends past the end of the line
               lineCounter++;
               phraseCounter = 0;
               phrase = "";
            }
            
            phrase += " " + line.split(" ")[lineCounter + phraseCounter];
            phrase = phrase.trim();
            incrementHashMap(historyCounts, phrase); // Increments value of the phrase stored in historyCounts
            
            if (phraseCounter > 0) {
               incrementHashMap(ngramCounts, phrase); // increments value of the phrase stored in ngramCounts if the phrase is more than 1 word
            }
            if (phraseCounter == 0 && !tempVocab.contains(phrase)) { // Adds new word to the arraylist vocab if the phrase is one word and is not already in the vocab
               vocab.add(phrase);
               tempVocab.add(phrase);
            }
            
            phraseCounter++;
         }   
      }
   }

    //  - This.p.get(ngram) contains the conditional probability P(w|h) for ngram (h,w) 
    //      only non-zero probabilities are stored in this.p

   private void convertCountsToProbabilities(HashMap<String,Integer> ngramCounts, HashMap<String,Integer> historyCounts) {
      for (Map.Entry<String,Integer> map : ngramCounts.entrySet()) {
         double probability;
         int index = map.getKey().split(" ").length;
         String key = "";
         
         for (int i = 0; i < index - 1; i++) {
            key += " " + map.getKey().split(" ")[i];
         }

         key = key.trim();
         probability = (double)(ngramCounts.get(map.getKey())) / (double)(historyCounts.get(key)); // Calculates probability of the word happening after the given history 

         if (probability > 0) {
            p.put(map.getKey(), probability);
         }      
      }
   }

   private void incrementHashMap(HashMap<String,Integer> map, String key) {
      if (map.get(key) != null) {
        map.put(key, map.get(key) + 1);
      }
      else {
         map.put(key, 1);
      }
   }

   public static String arrayToString(List<String> sequence) {
      java.lang.StringBuilder builder = new java.lang.StringBuilder();

      if (sequence.size() == 0) {
         return "";
      }

      builder.append(sequence.get(0));

      for (int i=1; i<sequence.size(); i++) {
         builder.append(" " + sequence.get(i));
      }
      
      return builder.toString();
   }

   public static ArrayList<String> stringToArray(String s) {
      return new ArrayList<String>(java.util.Arrays.asList(s.split(" ")));
   }
}
