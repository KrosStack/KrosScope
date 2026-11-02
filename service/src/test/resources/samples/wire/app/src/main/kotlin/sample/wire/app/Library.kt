package sample.wire.app

import sample.wire.proto.Book
import sample.wire.proto.Genre
import sample.wire.proto.Shelf

class Library {
    fun book(title: String): Book = Book(title = title, pages = 10, genre = Genre.FICTION)

    fun titleOf(book: Book): String = book.title

    fun isFiction(book: Book): Boolean = book.genre == Genre.FICTION

    fun renamed(book: Book): Book = book.copy(title = "Renamed")

    fun encode(book: Book): ByteArray = Book.ADAPTER.encode(book)

    fun label(genre: Genre): String = when (genre) {
        Genre.GENRE_UNSPECIFIED -> "none"
        Genre.FICTION -> "fiction"
        Genre.SCIENCE -> "science"
    }

    fun publisherOf(book: Book): String = book.publisher?.name.orEmpty()

    fun countOf(shelf: Shelf): Int = shelf.books.size
}
