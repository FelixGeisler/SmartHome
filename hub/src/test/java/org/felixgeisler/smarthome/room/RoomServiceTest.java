package org.felixgeisler.smarthome.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.felixgeisler.smarthome.floor.Floor;
import org.felixgeisler.smarthome.floor.FloorNotFoundException;
import org.felixgeisler.smarthome.floor.FloorRemoved;
import org.felixgeisler.smarthome.floor.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class RoomServiceTest {

  private RoomRepository rooms;
  private FloorRepository floors;
  private ApplicationEventPublisher events;
  private RoomService service;

  @BeforeEach
  void setUp() {
    rooms = mock(RoomRepository.class);
    floors = mock(FloorRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service = new RoomService(rooms, floors, events);
  }

  @DisplayName("create() saves and returns a new room")
  @Test
  void create_savesNewRoom() {
    when(rooms.findByName("Kitchen")).thenReturn(Optional.empty());
    when(rooms.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room room = service.create("Kitchen");

    assertEquals("Kitchen", room.getName());
    verify(rooms).save(any(Room.class));
  }

  @DisplayName("create() rejects a duplicate name before saving")
  @Test
  void create_rejectsDuplicateName() {
    when(rooms.findByName("Kitchen")).thenReturn(Optional.of(new Room("Kitchen")));

    assertThrows(RoomAlreadyExistsException.class, () -> service.create("Kitchen"));
    verify(rooms, never()).save(any());
  }

  @DisplayName("create() maps a lost unique-name race to a conflict")
  @Test
  void create_mapsRaceToConflict() {
    when(rooms.findByName("Kitchen")).thenReturn(Optional.empty());
    when(rooms.save(any(Room.class))).thenThrow(new DataIntegrityViolationException("dup"));

    assertThrows(RoomAlreadyExistsException.class, () -> service.create("Kitchen"));
  }

  @DisplayName("getById() throws when the room is missing")
  @Test
  void getById_throwsWhenMissing() {
    when(rooms.findById(9L)).thenReturn(Optional.empty());

    assertThrows(RoomNotFoundException.class, () -> service.getById(9L));
  }

  @DisplayName("rename() changes the name when no other room has it")
  @Test
  void rename_changesName() {
    Room room = new Room("Kitchen");
    when(rooms.findById(1L)).thenReturn(Optional.of(room));
    when(rooms.findByName("Cookhouse")).thenReturn(Optional.empty());
    when(rooms.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room result = service.rename(1L, "Cookhouse");

    assertEquals("Cookhouse", result.getName());
  }

  @DisplayName("rename() rejects a name already used by another room")
  @Test
  void rename_rejectsDuplicateName() {
    when(rooms.findById(1L)).thenReturn(Optional.of(new Room("Kitchen")));
    when(rooms.findByName("Hall")).thenReturn(Optional.of(new Room("Hall")));

    assertThrows(RoomAlreadyExistsException.class, () -> service.rename(1L, "Hall"));
    verify(rooms, never()).save(any());
  }

  @DisplayName("delete() publishes RoomRemoved and then removes the room")
  @Test
  void delete_publishesRoomRemovedThenDeletes() {
    when(rooms.existsById(1L)).thenReturn(true);

    service.delete(1L);

    verify(events).publishEvent(new RoomRemoved(1L));
    verify(rooms).deleteById(1L);
  }

  @DisplayName("delete() throws when the room is missing")
  @Test
  void delete_throwsWhenMissing() {
    when(rooms.existsById(9L)).thenReturn(false);

    assertThrows(RoomNotFoundException.class, () -> service.delete(9L));
    verify(rooms, never()).deleteById(any());
  }

  @DisplayName("assignFloor() puts the room on the floor")
  @Test
  void assignFloor_assignsRoomToFloor() {
    Room room = new Room("Kitchen");
    Floor floor = new Floor("Ground", 0);
    when(rooms.findById(1L)).thenReturn(Optional.of(room));
    when(floors.findById(2L)).thenReturn(Optional.of(floor));
    when(rooms.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room result = service.assignFloor(1L, 2L);

    assertEquals(floor, result.getFloor());
  }

  @DisplayName("assignFloor() throws when the floor is missing")
  @Test
  void assignFloor_throwsWhenFloorMissing() {
    when(rooms.findById(1L)).thenReturn(Optional.of(new Room("Kitchen")));
    when(floors.findById(2L)).thenReturn(Optional.empty());

    assertThrows(FloorNotFoundException.class, () -> service.assignFloor(1L, 2L));
    verify(rooms, never()).save(any());
  }

  @DisplayName("clearFloor() takes the room off its floor")
  @Test
  void clearFloor_unassignsRoom() {
    Room room = new Room("Kitchen");
    room.assignFloor(new Floor("Ground", 0));
    when(rooms.findById(1L)).thenReturn(Optional.of(room));
    when(rooms.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room result = service.clearFloor(1L);

    assertNull(result.getFloor());
  }

  @DisplayName("onFloorRemoved() unassigns the rooms on the removed floor")
  @Test
  void onFloorRemoved_unassignsRoomsOnFloor() {
    Room room = new Room("Kitchen");
    room.assignFloor(new Floor("Ground", 0));
    when(rooms.findByFloorId(2L)).thenReturn(List.of(room));
    when(rooms.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onFloorRemoved(new FloorRemoved(2L));

    assertNull(room.getFloor());
    verify(rooms).save(room);
  }
}
