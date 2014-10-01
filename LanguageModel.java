/* 
 * LanguageModel.java
 *
 * Implements methods for training a language model from a text file,
 * writing a vocabulary, and randomly completing sentences 
 * 
 * Evan Ricks & Iain Maryanow
 * January 2014
 *
 */

import java.util.*;
import java.io.*;

public class LanguageModel 
{
    HashMap<String,Double> p;         // maps ngrams to conditional probabilities
    ArrayList<String> vocab;          // stores the unique words in the input text
    int maxOrder;                     // maximum n-gram order to compute
    java.util.Random generator;       // a random number generator object

    // Constructor
        
    // LanguageModel
    // Preconditions:
    //    - textFilename is the name of a plaintext training file
    //    - maxOrder is the maximum n-gram order for which to estimate counts
    //  - generator is java.util.Random object
    //  - vocabFilename is the name where the vocab file will be written
    //        vocabFilename can also be null
    //  - countsFilename is the name where the counts will be written
    //        countsFilename can also be null
    // Postconditions:
    //  - this.p maps ngrams (h,w) to the the maximum likelihood estimates 
    //    of P(w|h) for all n-grams up to maxOrder 
    //    Only non-zero probabilities should be stored in this map
    //  - this.vocab contains each word appearing in textFilename exactly once
    //    in the order they appear in textFilename
    //  - this.maxOrder is assignment maxOrder
    //  - this.generator is assignment generator
    //  - If vocabFilename is non-null, the vocabulary words are printed to it, one per line, in order
    //  - If countsFilename is non-null, the ngram counts words are printed to countsFilename, 
    //    each line has the ngram, then a tab, then the number of times that ngram appears
    //    (within a line order matters, but the order of the lines themselves doesn't) 
    // Notes:
    //  - n-gram and history counts should be computed with a call to getCounts
    //  - File saving should be accomplished by calls to saveVocab and saveCounts
    //  - convertCountsToProbabilities should be used to then get the probabilities
    //  - If opening any file throws a FileNotFoundException, print to standard error:
    //        "Error: Unable to open file " + filename
    //        (where filename contains the name of the problem file)
    //      and then exit with value 1 (i.e. System.exit(1))
   public LanguageModel(String textFilename, int maxOrder, java.util.Random generator, String vocabFile, String countsFile) 
   {
      this.maxOrder = maxOrder;
      this.generator = generator;
      //initializations
      HashMap<String,Integer> ngramCounts = new HashMap<String,Integer>();
      HashMap<String,Integer> historyCounts = new HashMap<String,Integer>();
      p = new HashMap<String,Double>();
      vocab = new ArrayList<String>();        

      try(java.util.Scanner input = new java.util.Scanner(new java.io.File(textFilename))) //check for valid source text file
      {
         getCounts(input, ngramCounts, historyCounts, vocab, maxOrder);
      }
      catch(FileNotFoundException ex)
      {
         System.err.println("Error: Unable to open file " + textFilename);
         System.exit(1);
      }
      saveVocab(vocabFile); // will print each word from vocab to the file given if it is not null
      saveCounts(countsFile, ngramCounts); //will print each key, a tab, and then the key's value to the file given if it is not null
      convertCountsToProbabilities(ngramCounts, historyCounts);
   }
       // Accessors

    // getMaxOrder
    // Preconditions:
    //  - None
    // Postconditions:
    //  - this.maxOrder is returned
   public int getMaxOrder()  
   {
      return maxOrder;
   }

    // randomCompletion
    // Preconditions:
    //  - history contains an initial history to complete
    //  - order is the n-gram order to use when completing the sentence
    // Postconditions:
    //  - history must not be modified (i.e. make a copy of it)
    //  - Until </s> or <fail> is drawn:
    //    1) Draw a new word w according to P(w|h)
    //    2) Print a space and then w
    //    3) w is added to the history h
    //   Once </s> or <fail> is reached, print that token and then a newline
    // Notes:
    //  - Call randomNextWord to draw each new word
   public void randomCompletion(ArrayList<String> history, int order) 
   {
      boolean flag = true;
      ArrayList<String> tempHistory = new ArrayList<String>(history);
      String wordDrawn; 
      while(flag)
      {
         wordDrawn = randomNextWord(tempHistory, order); //stores the word that will be drawn from randomNextWord given history and order
         if(wordDrawn.equals("</s>") || wordDrawn.equals("<fail>")) //stops while loop when end of sentence or fail is reached
         {
            flag = false; 
         }
         System.out.print(" " + wordDrawn);
         tempHistory.add(wordDrawn); //adds drawn word to the end of the temporary history array list
      }
      System.out.println();
   }

    // Private Helper Methods

    // saveVocab
    // Preconditions:
    //  - vocabFilename is the name where the vocab file will be written
    //        vocabFilename can also be null
    // Postconditions:
    //  - this.vocab contains each word appearing in textFilename exactly once
    //    in the order they appear in textFilename
    //  - If opening the file throws a FileNotFoundException, print to standard error:
    //        "Error: Unable to open file " + vocabFilename
    //      and then exit with value 1 (i.e. System.exit(1))
   private void saveVocab(String vocabFilename) //if a text file is provided in the command line arguments, will write all vocab words from the arraylist to the file, line by line
   {    
      if(vocabFilename != null) //skips method if no text file is provided
      {
         try //tries to write to file
         {
            PrintWriter toFile = new PrintWriter(vocabFilename);
            for (int i = 0; i < vocab.size(); i++) 
            {
               toFile.println(vocab.get(i));
            }
            toFile.close();
         }
         catch(FileNotFoundException ex) //prints error if unable to open file
         {
            System.err.println("Error: Unable to open file " + vocabFilename);
            System.exit(1);
         }
      }
   }

    // saveCounts
    // Preconditions:
    //  - countsFilename is the name where the counts will be written
    //     countsFilename can also be null
    //  - ngramCounts.get(ngram) returns the number of times ngram appears
    //     ngrams with count 0 are not included
    // Postconditions:
    //  - If countsFilename is non-null, the ngram counts words are printed to countsFilename, 
    //     each line has the ngram, then a tab, then the number of times that ngram appears
    //     (within a line order matters, but the order of the lines themselves doesn't) 
    // Notes:
    //  - If opening the file throws a FileNotFoundException, print to standard error:
    //       "Error: Unable to open file " + countsFilename
    //      and then exit with value 1 (i.e. System.exit(1))
   private void saveCounts(String countsFilename, HashMap<String,Integer> ngramCounts) //if a text file is provided in the command line arguments, will write all ngrams line by line with a tab and then the number of times that they appear in the source text
   {
      if(countsFilename != null) //skips method if text file is not provided
      {
         try //tries to write to file
         {
            PrintWriter toFile = new PrintWriter(countsFilename);
            for(Map.Entry<String,Integer> map : ngramCounts.entrySet()) 
            {
               toFile.println(map.getKey() + "\t" + map.getValue());
            }
            toFile.close();
         }
         catch(FileNotFoundException ex) //prints error if unable to open file
         {
            System.err.println("Error: Unable to open file " + countsFilename);
            System.exit(1);
         }
      }
   }

    // randomNextWord
    // Preconditions:
    //  - history is the history on which to condition the draw
    //  - order is the order of n-gram to use 
    //      (i.e. no more than n-1 history words)
    //  - this.generator is the generator passed to the constructor
    // Postconditions:
    //  - A new word is drawn (see assignment description for the algorithm to use)
    //  - If no word follows that history for the specified order, return "<fail>"
    // Notes:
    //  - The nextDouble() method draws a random number between 0 and 1
    //  - ArrayList has a subList method to return an array slice
   private String randomNextWord(ArrayList<String> history, int order) //draws a random next word based on a random number generator given a history and order
   {
      double randomNum = this.generator.nextDouble();
      double cumulativeSum = 0;
      String key = this.arrayToString(history); //converts history to a string
      if(history.size() >= order) //trims history if greater than order size - 1
      {
         key = this.arrayToString(history.subList(history.size() - order + 1, history.size()));
      }
      for(int i = 0; i < vocab.size() - 1; i++) //adds probabilities of all vocab words happening after the history
      {
         if(this.p.get(key + " " + vocab.get(i)) != null)
         {
            cumulativeSum += this.p.get(key + " " + vocab.get(i));
         }
         if(cumulativeSum > randomNum) //returns the current vocab word when the probabilities becomes greater than the random number generated
         {
            return vocab.get(i);     
         }
      }
      if(cumulativeSum == 0) //returns fail if no words ever follow the given history
      {
         return "<fail>";
      }
      return vocab.get(vocab.size() - 1); //returns last vocab word in the arraylist if the cumulative probabilities never reach the random number generated (for example when the random number generated is or is very close to 1)
   } 

    // getCounts
    // Preconditions:
    //  - input is an initialized Scanner object associated with the text input file
    //  - ngramCounts is an empty (but non-null) HashMap
    //  - historyCounts is an empty (but non-null) HashMap
    //  - vocab is an empty (but non-null) ArrayList
    //  - maxOrder is the maximum order n-gram for which to extract counts
    // Postconditions:
    //  - ngramCounts.get(ngram) contains the number of times that ngram appears in the input
    //      ngram must be 2+ words long (e.g. "<s> i")
    //  - historyCounts.get(history) contains the number of times that ngram history appears in the input
    //      histories can be a single word (e.g. "<s>")
    //  - vocab contains each word in the input file exactly once, in the order they appear in the input
    // Notes:
    //  - You may find it useful to implement helper function incrementHashMap and use it
   private void getCounts(java.util.Scanner input,HashMap<String,Integer> ngramCounts,HashMap<String,Integer> historyCounts,ArrayList<String> vocab,int maxOrder)
   {
      String line;
      HashSet<String> tempVocab = new HashSet<String>(); //create a hash set to pass the ngrams through to check for duplicates
      while(input.hasNextLine()) //walks through the source file, line by line
      {
         line = input.nextLine();
         int lineLength = line.split(" ").length;
         String phrase = "";
         int lineCounter = 0;
         int phraseCounter = 0;
         while(lineCounter < lineLength - 1) //steps through the line, word by word
         {
            if(phraseCounter >= maxOrder || phraseCounter + lineCounter >= lineLength) //increments lineCounter if the ngram length becomes greater than the maxOrder or extends past the end of the line
            {
               lineCounter++;
               phraseCounter = 0;
               phrase = "";
            }
            phrase += " " + line.split(" ")[lineCounter + phraseCounter];
            phrase = phrase.trim();
            incrementHashMap(historyCounts, phrase); //increments value of the phrase stored in historyCounts
            if(phraseCounter > 0)
            {
               incrementHashMap(ngramCounts, phrase); //increments value of the phrase stored in ngramCounts if the phrase is more than 1 word
            }
            if(phraseCounter == 0 && !tempVocab.contains(phrase)) //adds new word to the arraylist vocab if the phrase is one word and is not already in the vocab
            {
               vocab.add(phrase);
               tempVocab.add(phrase);
            }
            phraseCounter++;
         }   
      }
   }

    // convertCountsToProbabilities
    // Preconditions:
    //  - ngramCounts.get(ngram) contains the number of times that ngram appears in the input
    //  - historyCounts.get(history) contains the number of times that ngram history appears in the input
    // Postconditions:
    //  - this.p.get(ngram) contains the conditional probability P(w|h) for ngram (h,w) 
    //      only non-zero probabilities are stored in this.p
   private void convertCountsToProbabilities(HashMap<String,Integer> ngramCounts,HashMap<String,Integer> historyCounts) 
   {
      for(Map.Entry<String,Integer> map : ngramCounts.entrySet()) //walks through each ngram stored in ngramCounts
      {
         double probability;
         int index = map.getKey().split(" ").length;
         String key = "";
         for(int i = 0; i < index - 1; i++) //creates string of the ngram minus the last word 
         {
            key += " " + map.getKey().split(" ")[i];
         }
         key = key.trim();
         probability = (double)(ngramCounts.get(map.getKey())) / (double)(historyCounts.get(key)); //calculates probability of the word happening after the given history
         if(probability > 0) //only adds non-zero probabilities to p
         {  
            p.put(map.getKey(), probability);
         }      
      }
   }

    // incrementHashMap
    // Preconditions:
    //  - map is a non-null HashMap 
    //  - key is a key that may or may not be in map
    // Postconditions:
    //  - If key was already in map, map.get(key) returns 1 more than it did before
    //  - If key was not in map, map.get(key) returns 1
    // Notes
    //  - This method is useful, but optional
   private void incrementHashMap(HashMap<String,Integer> map,String key)
   {
      if(map.get(key) != null) //increments value of the existing key by 1 
      {
        map.put(key, map.get(key) + 1);
      }
      else //creates new key in the hashmap with a value of 1
      {
         map.put(key, 1);
      }
   }

    // Static Methods

    // arrayToString
    // Preconditions:
    //  - sequence is a List (e.g. ArrayList) of Strings
    // Postconditions:
    //  - sequence is returned in string form, each element joined by a single space
    //  - If sequence was length 0, the empty string is returned
    // Notes:
    //  - Already implemented for you
   public static String arrayToString(List<String> sequence) 
   {
      java.lang.StringBuilder builder = new java.lang.StringBuilder();
      if( sequence.size() == 0 ) 
      {
         return "";
      }
      builder.append(sequence.get(0));
      for( int i=1; i<sequence.size(); i++ ) 
      {
         builder.append(" " + sequence.get(i));
      }
      return builder.toString();
   }

    // stringToArray
    // Preconditions: 
    //  - s is a string of words, each separated by a single space
    // Postconditions:
    //  - An ArrayList is returned containing the words in s
    // Notes:
    //  - Already implemented for you
   public static ArrayList<String> stringToArray(String s) 
   {
      return new ArrayList<String>(java.util.Arrays.asList(s.split(" ")));
   }
}
