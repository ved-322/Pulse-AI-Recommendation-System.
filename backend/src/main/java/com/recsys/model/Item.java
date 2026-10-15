package com.recsys.model;

import java.util.List;

/**
 * A recommendable product / piece of content.
 *
 * @param slug  stable URL-safe key, also the file name of the product photo (img/&lt;slug&gt;.jpg)
 * @param brand the maker - or the author / director for books and movies
 * @param meta  extra detail such as the release year for books and movies (may be empty)
 */
public record Item(long id, String slug, String name, String brand, String category, String description,
                   double price, List<String> tags, String meta, String emoji, String accent) {
}
