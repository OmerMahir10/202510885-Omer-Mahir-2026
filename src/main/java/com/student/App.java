package com.student;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Scanner;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Book Explorer
 * Console app that reads real pages from Books to Scrape (books.toscrape.com)
 * with JSoup and lets the user keep a small personal reading list.
 */
public class App {

    // ---------- Settings ----------
    static final String BASE_URL = "https://books.toscrape.com/";
    static final int RESULT_SIZE = 25;   // books kept from the last explore/search
    static final int LIST_SIZE = 20;      // reading list capacity (fixed-size array)

    // ---------- Data: plain arrays only, no ArrayList ----------
    static Book[] results = new Book[RESULT_SIZE];
    static int resultCount = 0;

    static Book[] readingList = new Book[LIST_SIZE];
    static int listCount = 0;

    static Scanner input = new Scanner(System.in);

    // One book = title + price + rating + availability + link
    static class Book {
        String title;
        double price;
        int rating;          // 1 to 5 stars (0 if unknown)
        String stock;
        String url;

        Book(String title, double price, int rating, String stock, String url) {
            this.title = title;
            this.price = price;
            this.rating = rating;
            this.stock = stock;
            this.url = url;
        }
    }

    // ---------- Main menu ----------
    public static void main(String[] args) {
        // so the pound sign and accents print correctly
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        int choice;
        do {
            showMenu();
            choice = readNumber("Enter your choice: ");

            switch (choice) {
                case 1:
                    exploreBooks();
                    break;
                case 2:
                    System.out.print("Enter a title to search: ");
                    searchBook(input.nextLine());
                    break;
                case 3:
                    searchByPrice();
                    break;
                case 4:
                    addBook();
                    break;
                case 5:
                    viewReadingList();
                    break;
                case 6:
                    removeBook();
                    break;
                case 7:
                    displayBookDetails();
                    break;
                case 0:
                    System.out.println("\nGoodbye, happy reading!");
                    break;
                default:
                    System.out.println("\nInvalid choice. Please enter a number from 0 to 7.");
            }
        } while (choice != 0);
    }

    static void showMenu() {
        System.out.println();
        System.out.println("===== BOOK EXPLORER =====");
        System.out.println("1. Explore Books");
        System.out.println("2. Search by Title");
        System.out.println("3. Search by Maximum Price");
        System.out.println("4. Add Book to Reading List");
        System.out.println("5. View Reading List");
        System.out.println("6. Remove Book");
        System.out.println("7. Display Book Details");
        System.out.println("0. Exit");
    }

    // ---------- 1. Explore ----------
    static void exploreBooks() {
        System.out.println("\nLoading books from Books to Scrape...");
        if (!scanCatalogue(null, -1)) {
            return;
        }
        if (resultCount == 0) {
            System.out.println("No books found. The page layout may have changed.");
            return;
        }
        showResults("Books");
    }

    // ---------- 2 and 3. Search (overloaded) ----------
    // Search by title
    static void searchBook(String title) {
        String word = title.trim();
        if (word.isEmpty()) {
            System.out.println("\nYou did not type anything to search for.");
            return;
        }

        System.out.println("\nSearching by title: " + word + " ...");
        if (scanCatalogue(word, -1)) {
            finishSearch("No books found with \"" + word + "\" in the title.");
        }
    }

    // Search by maximum price (same name, different parameter type)
    static void searchBook(double maxPrice) {
        System.out.println("\nSearching for books up to " + money(maxPrice) + " ...");
        if (scanCatalogue(null, maxPrice)) {
            finishSearch("No books found up to " + money(maxPrice) + ".");
        }
    }

    static void searchByPrice() {
        double maxPrice = readPrice("Enter the maximum price in pounds (e.g. 20 or 12.50): ");
        if (maxPrice < 0) {
            System.out.println("\nPlease type a valid price, like 20 or 12.50.");
            return;
        }
        searchBook(maxPrice);
    }

    static void finishSearch(String notFoundMessage) {
        if (resultCount == 0) {
            System.out.println(notFoundMessage);
        } else {
            showResults("Search results");
        }
    }

    // ---------- Scraping ----------
    // The site has no search page, so we walk through the catalogue pages
    // (20 books each) and keep the books that pass the filter.
    // titleWord == null means "any title"; maxPrice < 0 means "any price".
    // Stops as soon as RESULT_SIZE books are found. Returns false on a connection error.
    static boolean scanCatalogue(String titleWord, double maxPrice) {
        resultCount = 0;
        String url = BASE_URL;
        System.out.print("Scanning catalogue pages ");

        while (url != null && resultCount < RESULT_SIZE) {
            Document doc;
            try {
                doc = fetchPage(url);
            } catch (IOException e) {
                System.out.println();
                resultCount = 0;
                showConnectionError(e);
                return false;
            }
            System.out.print(".");
            collectBooks(doc, titleWord, maxPrice);

            Element next = doc.selectFirst("li.next a");
            url = (next == null) ? null : next.absUrl("href");
        }
        System.out.println();
        return true;
    }

    // Reads one catalogue page: every book is <article class="product_pod">
    static void collectBooks(Document doc, String titleWord, double maxPrice) {
        Elements pods = doc.select("article.product_pod");

        for (Element pod : pods) {
            if (resultCount == RESULT_SIZE) {
                break;
            }

            Element link = pod.selectFirst("h3 a");
            Element priceTag = pod.selectFirst("p.price_color");
            Element ratingTag = pod.selectFirst("p.star-rating");
            Element stockTag = pod.selectFirst("p.availability");
            if (link == null || priceTag == null) {
                continue;
            }

            String title = link.attr("title").trim();      // the link text is shortened with "..."
            if (title.isEmpty()) {
                title = link.text().trim();
            }
            double price = parsePrice(priceTag.text());
            int rating = (ratingTag == null) ? 0 : ratingToNumber(ratingTag.className());
            String stock = (stockTag == null) ? "Unknown" : stockTag.text().trim();
            String url = link.absUrl("href");

            if (title.isEmpty() || price < 0) {
                continue;
            }
            if (titleWord != null && !containsAllWords(title, titleWord)) {
                continue;
            }
            if (maxPrice >= 0 && price > maxPrice) {
                continue;
            }
            results[resultCount++] = new Book(title, price, rating, stock, url);
        }
    }

    // "£51.77" -> 51.77 (returns -1 if the text has no number)
    static double parsePrice(String text) {
        String digits = text.replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // class="star-rating Three" -> 3
    static int ratingToNumber(String className) {
        String[] parts = className.trim().split("\\s+");
        switch (parts[parts.length - 1]) {
            case "One":
                return 1;
            case "Two":
                return 2;
            case "Three":
                return 3;
            case "Four":
                return 4;
            case "Five":
                return 5;
            default:
                return 0;
        }
    }

    // ---------- 4. Add ----------
    static void addBook() {
        if (resultCount == 0) {
            System.out.println("\nNo books to choose from yet. Use Explore or Search first.");
            return;
        }
        if (listCount == LIST_SIZE) {
            System.out.println("\nYour reading list is full (" + LIST_SIZE + " books). Remove one first.");
            return;
        }

        showResults("Last results");
        int index = pickNumber(resultCount, "Enter the number of the book to add (0 to cancel): ");
        if (index == -1) {
            return;
        }

        Book book = results[index];
        for (int i = 0; i < listCount; i++) {
            if (readingList[i].url.equals(book.url)) {
                System.out.println("\"" + book.title + "\" is already in your reading list.");
                return;
            }
        }

        readingList[listCount] = book;
        listCount++;
        System.out.println("Added: " + book.title);
    }

    // ---------- 5. View ----------
    static void viewReadingList() {
        if (listCount == 0) {
            System.out.println("\nYour reading list is empty.");
            return;
        }

        System.out.println("\n--- Your Reading List (" + listCount + "/" + LIST_SIZE + ") ---");
        for (int i = 0; i < listCount; i++) {
            System.out.println((i + 1) + ". " + readingList[i].title);
            System.out.println("   " + summary(readingList[i]));
        }
    }

    // ---------- 6. Remove ----------
    static void removeBook() {
        if (listCount == 0) {
            System.out.println("\nYour reading list is empty, nothing to remove.");
            return;
        }

        viewReadingList();
        int index = pickNumber(listCount, "Enter the number of the book to remove (0 to cancel): ");
        if (index == -1) {
            return;
        }

        String removed = readingList[index].title;
        for (int i = index; i < listCount - 1; i++) {   // shift the rest one place left
            readingList[i] = readingList[i + 1];
        }
        readingList[listCount - 1] = null;
        listCount--;
        System.out.println("Removed: " + removed);
    }

    // ---------- 7. Details ----------
    static void displayBookDetails() {
        System.out.println("\nWhich list do you want to pick from?");
        System.out.println("1. Last explore/search results");
        System.out.println("2. My reading list");
        int source = readNumber("Choose 1 or 2: ");

        Book[] list;
        int count;
        if (source == 1) {
            list = results;
            count = resultCount;
        } else if (source == 2) {
            list = readingList;
            count = listCount;
        } else {
            System.out.println("Invalid choice.");
            return;
        }

        if (count == 0) {
            System.out.println("There are no books in that list yet.");
            return;
        }

        if (source == 1) {
            showResults("Last results");
        } else {
            viewReadingList();
        }

        int index = pickNumber(count, "Enter the book number (0 to cancel): ");
        if (index == -1) {
            return;
        }

        Book book = list[index];
        System.out.println("\n===== BOOK DETAILS =====");
        System.out.println("Title       : " + book.title);
        System.out.println("Price       : " + money(book.price));
        System.out.println("Rating      : " + book.rating + "/5");
        System.out.println("Availability: " + book.stock);
        System.out.println("URL         : " + book.url);
    }

    // ---------- Helpers ----------
    static Document fetchPage(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();
    }

    static void showConnectionError(IOException e) {
        String reason = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
        System.out.println("Connection error: could not load the page from Books to Scrape.");
        System.out.println("Check your internet connection and try again. (" + reason + ")");
    }

    static void showResults(String heading) {
        System.out.println("\n--- " + heading + " (" + resultCount + " books) ---");
        for (int i = 0; i < resultCount; i++) {
            System.out.println((i + 1) + ". " + results[i].title);
            System.out.println("   " + summary(results[i]));
        }
    }

    // "£51.77 | 3/5 stars | In stock"
    static String summary(Book book) {
        return money(book.price) + " | " + book.rating + "/5 stars | " + book.stock;
    }

    // \u00a3 is the pound sign
    static String money(double amount) {
        return "\u00a3" + String.format(Locale.US, "%.2f", amount);
    }

    // true if every word of the keyword appears in the text (ignores case and accents)
    static boolean containsAllWords(String text, String keyword) {
        String plainText = plain(text);
        String[] words = plain(keyword).split("\\s+");
        for (String w : words) {
            if (!plainText.contains(w)) {
                return false;
            }
        }
        return true;
    }

    static String plain(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase();
    }

    // Reads a whole number; returns -1 if the user typed something else
    static int readNumber(String prompt) {
        System.out.print(prompt);
        String line = input.nextLine().trim();
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Reads a price like 20 or 12.50; returns -1 if the user typed something else
    static double readPrice(String prompt) {
        System.out.print(prompt);
        String line = input.nextLine().trim().replace(',', '.');
        if (!line.matches("\\d+(\\.\\d+)?")) {
            return -1;
        }
        return Double.parseDouble(line);
    }

    // Asks for a number from 1 to count. Returns the array index (0-based),
    // or -1 if the user cancelled (0) or typed something invalid.
    static int pickNumber(int count, String prompt) {
        int n = readNumber(prompt);
        if (n == 0) {
            System.out.println("Cancelled.");
            return -1;
        }
        if (n < 1 || n > count) {
            System.out.println("Invalid number. Please pick between 1 and " + count + ".");
            return -1;
        }
        return n - 1;
    }
}
