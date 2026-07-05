package org.felixgeisler.smarthome.floor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class FloorServiceTest {

  private FloorRepository floors;
  private ApplicationEventPublisher events;
  private FloorService service;

  @BeforeEach
  void setUp() {
    floors = mock(FloorRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service = new FloorService(floors, events);
  }

  @DisplayName("create() saves a new floor one level above the current highest")
  @Test
  void create_savesNewFloorAboveExisting() {
    when(floors.findByName("First")).thenReturn(Optional.empty());
    when(floors.findFirstByOrderByLevelDesc()).thenReturn(Optional.of(new Floor("Ground", 1)));
    when(floors.save(any(Floor.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Floor floor = service.create("First");

    assertEquals("First", floor.getName());
    assertEquals(2, floor.getLevel());
    verify(floors).save(any(Floor.class));
  }

  @DisplayName("create() rejects a duplicate name before saving")
  @Test
  void create_rejectsDuplicateName() {
    when(floors.findByName("Ground")).thenReturn(Optional.of(new Floor("Ground", 0)));

    assertThrows(FloorAlreadyExistsException.class, () -> service.create("Ground"));
    verify(floors, never()).save(any());
  }

  @DisplayName("create() maps a lost unique-name race to a conflict")
  @Test
  void create_mapsRaceToConflict() {
    when(floors.findByName("Ground")).thenReturn(Optional.empty());
    when(floors.save(any(Floor.class))).thenThrow(new DataIntegrityViolationException("dup"));

    assertThrows(FloorAlreadyExistsException.class, () -> service.create("Ground"));
  }

  @DisplayName("getById() throws when the floor is missing")
  @Test
  void getById_throwsWhenMissing() {
    when(floors.findById(9L)).thenReturn(Optional.empty());

    assertThrows(FloorNotFoundException.class, () -> service.getById(9L));
  }

  @DisplayName("rename() changes the name when no other floor has it")
  @Test
  void rename_changesName() {
    Floor floor = new Floor("Ground", 0);
    when(floors.findById(1L)).thenReturn(Optional.of(floor));
    when(floors.findByName("Basement")).thenReturn(Optional.empty());
    when(floors.save(any(Floor.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Floor result = service.rename(1L, "Basement");

    assertEquals("Basement", result.getName());
  }

  @DisplayName("rename() rejects a name already used by another floor")
  @Test
  void rename_rejectsDuplicateName() {
    when(floors.findById(1L)).thenReturn(Optional.of(new Floor("Ground", 0)));
    when(floors.findByName("Attic")).thenReturn(Optional.of(new Floor("Attic", 1)));

    assertThrows(FloorAlreadyExistsException.class, () -> service.rename(1L, "Attic"));
    verify(floors, never()).save(any());
  }

  @DisplayName("delete() publishes FloorRemoved and then removes the floor")
  @Test
  void delete_publishesFloorRemovedThenDeletes() {
    when(floors.existsById(1L)).thenReturn(true);

    service.delete(1L);

    verify(events).publishEvent(new FloorRemoved(1L));
    verify(floors).deleteById(1L);
  }

  @DisplayName("delete() throws when the floor is missing")
  @Test
  void delete_throwsWhenMissing() {
    when(floors.existsById(9L)).thenReturn(false);

    assertThrows(FloorNotFoundException.class, () -> service.delete(9L));
    verify(floors, never()).deleteById(any());
  }
}
