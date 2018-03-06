package me.gorky.HuffmanEncoder;

import java.io.*;
import java.util.*;

class Node implements Comparable<Node> {

    int weight;
    Node left;
    Node right;

    Node() {}

    Node(Node left, Node right) {
        this.left = left;
        this.right = right;
        weight = left.weight + right.weight;
    }

    @Override
    public int compareTo(Node node) {
        return weight - node.weight;
    }

}

class Leaf extends Node {

    String character;

    Leaf(String character, int weight) {
        this.character = character;
        this.weight = weight;
    }

}

public class HuffmanEncoder {

    private static final String SOURCE_FILENAME = "source.txt";
    private static final String ENCODED_FILENAME = "encoded.txt";

    public void go() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Type 1 for encoding or 0 for decoding: ");
            int mode = Integer.parseInt(reader.readLine());

            long startTime = System.nanoTime();

            boolean result;
            String fileToCheck;

            if (mode > 0) {
                System.out.println("\nEncoding...");
                result = encode();
                fileToCheck = ENCODED_FILENAME;
            } else {
                System.out.println("\nDecoding...");
                result = decode();
                fileToCheck = SOURCE_FILENAME;
            }

            if (result) {
                long endTime = System.nanoTime();
                double duration = (double) (endTime - startTime) / 1000000;
                System.out.println("\nDone! Check " + fileToCheck + " for the result. Execution took " + duration + " milliseconds");
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private boolean encode() {
        try {
            // split all content into an array of characters
            String[] characters = readFromFile(SOURCE_FILENAME).split("");

            // form a map (table) of unique characters where 'key -> value' is 'character -> its weight (frequency, initially 0)'
            // set correct weight values by iterating over the array of characters and incrementing the weight of the entry with the corresponding character
            Map<String, Integer> weightTable = formWeightTable(characters);

            // make tree leaves from all weight table entries
            // step-by-step, link two most lightweight nodes (or leaves, on the first step) to a new one, giving it a combined weight of two, until only the root is left
            Node treeRoot = formBinaryTree(weightTable);

            // remembering the path - 0 for every left, 1 for every right turn - iterate through the tree down to its leaves
            // now, a path to a particular leaf - is its code
            // form a 'character -> its code' map
            Map<String, String> codeTable = formCodeTable(treeRoot);

            // encode the content using the code map and write the result to a file
            writeEncodedContent(characters, codeTable, weightTable);

        } catch (IOException ex) {
            ex.printStackTrace();

            return false;
        }

        return true;
    }

    private Map<String, Integer> formWeightTable(String[] characters) {
        Map<String, Integer> weightTable = new HashMap<>();

        for (String character : characters) {
            int weight = weightTable.containsKey(character) ? (weightTable.get(character) + 1) : 1;
            weightTable.put(character, weight);
        }

        return weightTable;
    }

    // returns root of the tree
    private Node formBinaryTree(Map<String, Integer> weightTable) {
        ArrayList<Node> nodes = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : weightTable.entrySet()) {
            nodes.add(new Leaf(entry.getKey(), entry.getValue()));
        }

        Collections.sort(nodes);

        while (nodes.size() > 1) {
            Node newNode = new Node(nodes.get(1), nodes.get(0));
            nodes.remove(0);
            nodes.remove(0);
            insertPreservingOrder(nodes, newNode);
        }

        return nodes.get(0);
    }

    private void insertPreservingOrder(ArrayList<Node> nodes, Node node) {
        for (int i = 0; i < nodes.size(); i++) {
            if (node.weight < nodes.get(i).weight) {
                nodes.add(i, node);
                return;
            }
        }

        nodes.add(node);
    }

    private Map<String, String> formCodeTable(Node treeRoot) {
        Map<String, String> codeTable = new HashMap<>();
        formCodeTable(treeRoot, "", codeTable);

        return codeTable;
    }

    private void formCodeTable(Node node, String code, Map<String, String> codeTable) {
        if (node instanceof Leaf) {
            Leaf leaf = (Leaf) node;
            code = code.isEmpty() ? "0" : code;
            codeTable.put(leaf.character, code);
        } else {
            formCodeTable(node.left, code + "0", codeTable);
            formCodeTable(node.right, code + "1", codeTable);
        }
    }

    private void writeEncodedContent(String[] characters, Map<String, String> codeTable, Map<String, Integer> weightTable) throws IOException {
        String encodedContent = "";

        for (String character : characters) {
            encodedContent += codeTable.get(character);
        }

        String encodingTable = "";
        double bitsPerChar = (double) encodedContent.length() / characters.length;

        System.out.println("\nCode table (" + bitsPerChar + " bits per symbol on average):");

        for (Map.Entry<String, String> entry : codeTable.entrySet()) {
            String separator = encodingTable.isEmpty() ? "" : ";";
            String character = entry.getKey();
            int unicode = (int) character.charAt(0);
            int weight = weightTable.get(character);
            String code = entry.getValue();

            encodingTable += separator + character + ":" + code;

            String str = character + " (Unicode " + unicode + ", weight " + weight + "): " + code;
            System.out.println(str);
        }

        writeToFile(ENCODED_FILENAME, encodingTable + "\n" + encodedContent);
    }

    private boolean decode() {
        try {
            // read the contents of the encoded file
            String encodedContent = readFromFile(ENCODED_FILENAME);

            // validate the encoded content against a complex regex
            if (!ensureIntegrity(encodedContent)) {
                throw new IOException("Invalid encoding");
            }

            // split the content by lines into an array
            String[] lines = encodedContent.split("\n");
            // the last line is the meaningful content represented by 0s and 1s, all others - encoding table
            String[] zeroesAndOnes = lines[lines.length - 1].split("");

            // using regex and cycles, break down the lines (except for the last one) into a reverse ('code -> corresponding character') code map
            Map<String, String> reverseCodeTable = restoreReverseCodeTable(lines);

            // iterate through the 0s and 1s and decode them using the reverse code map
            String decodedContent = restoreContent(zeroesAndOnes, reverseCodeTable);

            // write the decoded content into a file
            writeToFile(SOURCE_FILENAME, decodedContent);

        } catch (IOException ex) {
            ex.printStackTrace();

            return false;
        }

        return true;
    }

    private boolean ensureIntegrity(String encodedContent) {
        return encodedContent.matches("(?s)((.|\\r\\n):[01]+;)*((.|\\r\\n):[01]+\\r?\\n)[01]+");
    }

    private Map<String, String> restoreReverseCodeTable(String[] lines) {
        Map<String, String> reverseCodeTable = new HashMap<>();
        String codeTableStr = "";

        for (int i = 0; i < lines.length - 1; i++) {
            String separator = (i < lines.length - 2) ? "\n" : "";
            codeTableStr += lines[i] + separator;
        }

        String[] pairs = codeTableStr.split("(?<!;);");

        for (String p : pairs) {
            String[] pair = p.split(":(?!:)");
            reverseCodeTable.put(pair[1], pair[0]);
        }

        return reverseCodeTable;
    }

    private String restoreContent(String[] zeroesAndOnes, Map<String, String> reverseCodeTable) {
        String content = "";
        String code = "";

        for (int i = 0; i < zeroesAndOnes.length; i++) {
            code += zeroesAndOnes[i];

            if (reverseCodeTable.containsKey(code)) {
                content += reverseCodeTable.get(code);
                code = "";
            }
        }

        return content;
    }

    private String readFromFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            StringBuilder builder = new StringBuilder();
            String line = reader.readLine();

            if (line == null) {
                throw new IOException(filename + " is empty");
            }

            while (line != null) {
                builder.append(line);
                line = reader.readLine();

                if (line != null) {
                    builder.append("\n");
                }
            }

            return builder.toString();
        }
    }

    private void writeToFile(String filename, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, false))) {
            writer.write(content);
        }
    }

    public static void main(String[] args) {
        HuffmanEncoder encoder = new HuffmanEncoder();
        encoder.go();
    }
}