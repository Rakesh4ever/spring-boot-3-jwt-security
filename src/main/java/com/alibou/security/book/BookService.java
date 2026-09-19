package com.alibou.security.book;

import com.alibou.security.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository repository;

    @Transactional
    public Book save(BookRequest request) {
        if (request.id() == null) {
            return repository.save(Book.builder()
                    .author(request.author())
                    .isbn(request.isbn())
                    .build());
        }
        var book = repository.findById(request.id())
                .orElseThrow(() -> new ResourceNotFoundException("Book", String.valueOf(request.id())));
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        return repository.save(book);
    }

    @Transactional(readOnly = true)
    public List<Book> findAll() {
        return repository.findAll();
    }
}
