package com.alibou.security.book;

import com.alibou.security.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository repository;

    @InjectMocks
    private BookService bookService;

    @Test
    @DisplayName("save without an id creates a new book")
    void createBook() {
        var request = new BookRequest(null, "Alibou", "12345");
        var persisted = Book.builder().id(1).author("Alibou").isbn("12345").build();
        when(repository.save(any(Book.class))).thenReturn(persisted);

        Book saved = bookService.save(request);

        assertThat(saved.getId()).isEqualTo(1);
        verify(repository).save(any(Book.class));
    }

    @Test
    @DisplayName("save with an unknown id is rejected")
    void updateMissingBook() {
        var request = new BookRequest(99, "Alibou", "12345");
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.save(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("save with an existing id mutates the loaded book")
    void updateExistingBook() {
        var existing = Book.builder().id(1).author("Old").isbn("000").createdBy(7).build();
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Book saved = bookService.save(new BookRequest(1, "Alibou", "12345"));

        assertThat(saved.getAuthor()).isEqualTo("Alibou");
        assertThat(saved.getIsbn()).isEqualTo("12345");
        assertThat(saved.getCreatedBy()).isEqualTo(7);
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("findAll returns repository results")
    void findAll() {
        when(repository.findAll()).thenReturn(List.of(Book.builder().id(1).author("A").isbn("1").build()));

        assertThat(bookService.findAll()).hasSize(1);
    }
}
