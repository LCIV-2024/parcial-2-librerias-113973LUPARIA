package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día
    
    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final UserService userService;
    
    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {

        // TODO: Implementar la creación de una reserva
        // Validar que el usuario existe
        User user = userService.getUserEntity(requestDTO.getUserId());
        
        // Validar que el libro existe y está disponible
        Book book = bookRepository.findByExternalId(requestDTO.getBookExternalId())
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + requestDTO.getBookExternalId()));
        
        if (book.getAvailableQuantity() <= 0) {
            throw new RuntimeException("No hay ejemplares disponibles del libro: " + book.getTitle());
        }
        
        // Crear la reserva
        Reservation reserva = new Reservation();
        reserva.setUser(user);
        reserva.setBook(book);
        reserva.setRentalDays(requestDTO.getRentalDays());
        reserva.setStartDate(requestDTO.getStartDate());
        reserva.setExpectedReturnDate(requestDTO.getStartDate().plusDays(requestDTO.getRentalDays()));
        reserva.setDailyRate(book.getPrice());
        reserva.setTotalFee(calculateTotalFee(book.getPrice(), requestDTO.getRentalDays()));
        reserva.setLateFee(BigDecimal.ZERO);
        reserva.setStatus(Reservation.ReservationStatus.ACTIVE);
        
        Reservation savedReservation = reservationRepository.save(reserva);
        
        // Reducir la cantidad disponible
        bookService.decreaseAvailableQuantity(book.getExternalId());
        
        return convertToDTO(savedReservation);
    }
    
    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {

        // TODO: Implementar la devolución de un libro
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));
        
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }
        
        LocalDate fechaDevolucion = returnRequest.getReturnDate();
        reservation.setActualReturnDate(fechaDevolucion);
        
        // Calcular tarifa por demora si hay retraso
        if (fechaDevolucion.isAfter(reservation.getExpectedReturnDate())) {
            long diasTarde = fechaDevolucion.toEpochDay() - reservation.getExpectedReturnDate().toEpochDay();
            BigDecimal precioTardanza = calculateLateFee(reservation.getBook().getPrice(), diasTarde);
            reservation.setLateFee(precioTardanza);
            reservation.setTotalFee(reservation.getTotalFee().add(precioTardanza));
        }
        
        reservation.setStatus(Reservation.ReservationStatus.RETURNED);
        Reservation updatedReservation = reservationRepository.save(reservation);
        
        // Aumentar la cantidad disponible
        bookService.increaseAvailableQuantity(reservation.getBook().getExternalId());
        
        return convertToDTO(updatedReservation);
    }
    
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        return reservationRepository.findOverdueReservations().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {
        // TODO: Implementar el cálculo del total de la reserva
        return dailyRate.multiply(new BigDecimal(rentalDays));
    }
    
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        // 15% del precio del libro por cada día de demora
        // TODO: Implementar el cálculo de la multa por demora
        if (daysLate <= 0) {
            return BigDecimal.ZERO;
        }
        return bookPrice.multiply(LATE_FEE_PERCENTAGE).multiply(new BigDecimal(daysLate));
    }
    
    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}

