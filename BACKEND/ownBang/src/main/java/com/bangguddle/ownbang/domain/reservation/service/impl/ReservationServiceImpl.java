package com.bangguddle.ownbang.domain.reservation.service.impl;

import com.bangguddle.ownbang.domain.agent.entity.Agent;
import com.bangguddle.ownbang.domain.agent.workhour.entity.AgentWorkhour;
import com.bangguddle.ownbang.domain.agent.repository.AgentRepository;
import com.bangguddle.ownbang.domain.agent.workhour.repository.AgentWorkhourRepository;
import com.bangguddle.ownbang.domain.reservation.dto.*;
import com.bangguddle.ownbang.domain.reservation.entity.Reservation;
import com.bangguddle.ownbang.domain.reservation.entity.ReservationStatus;
import com.bangguddle.ownbang.domain.reservation.repository.ReservationRepository;
import com.bangguddle.ownbang.domain.reservation.service.ReservationService;
import com.bangguddle.ownbang.domain.room.entity.Room;
import com.bangguddle.ownbang.domain.room.repository.RoomRepository;
import com.bangguddle.ownbang.domain.user.entity.User;
import com.bangguddle.ownbang.domain.user.repository.UserRepository;
import com.bangguddle.ownbang.domain.video.entity.Video;
import com.bangguddle.ownbang.domain.video.entity.VideoStatus;
import com.bangguddle.ownbang.domain.video.repository.VideoRepository;
import com.bangguddle.ownbang.domain.webrtc.service.WebrtcSessionService;
import com.bangguddle.ownbang.domain.webrtc.service.impl.WebrtcSessionServiceImpl;
import com.bangguddle.ownbang.global.enums.NoneResponse;
import com.bangguddle.ownbang.global.handler.AppException;
import com.bangguddle.ownbang.global.response.SuccessResponse;
import io.openvidu.java.client.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.bangguddle.ownbang.global.enums.ErrorCode.*;
import static com.bangguddle.ownbang.global.enums.SuccessCode.*;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AgentWorkhourRepository agentWorkhourRepository;
    private final VideoRepository videoRepository;
    private final AgentRepository agentRepository;
    private final WebrtcSessionService webrtcSessionService;

    /**
     * 예약 생성 Service 메서드
     *
     * @param reservationRequest 예약 생성 DTO
     * @return SuccessResponse
     * @throws AppException 특정 매물과, 시간대, 이미 확정된 예약이 존재하는 경우(RESERVATION_DUPLICATED) 발생
     * @throws AppException 내가 이미 예약 신청/확정한 매물일 경우, RESERVATION_COMPLETED 발생
     */
    @Override
    @Transactional
    public SuccessResponse<NoneResponse> createReservation(Long userId, ReservationRequest reservationRequest) {

        Long roomId = reservationRequest.roomId();
        // Room과 User 객체 조회
        Room room = roomRepository.getById(roomId);
        User user = userRepository.getById(userId);
        LocalDateTime reservationTime = reservationRequest.reservationTime();

        // 룸 ID와 시간이 일치하는 확정된 예약이 이미 존재하는지 확인
        Optional<Reservation> existingReservation = reservationRepository.findByRoomIdAndTimeWithLock(roomId, reservationTime);

        if (existingReservation.isPresent()) {
            throw new AppException(RESERVATION_DUPLICATED);
        }
        // 이미 내가 예약 신청/확정 한 매물이라면,
        Optional<Reservation> completedReservation = reservationRepository.findByRoomIdAndUserIdAndStatusNot(roomId, userId, ReservationStatus.CANCELLED);
        if (completedReservation.isPresent()) {
            throw new AppException(RESERVATION_COMPLETED);
        }

        // 새 예약 저장
        Reservation reservation = reservationRequest.toEntity(room, user);
        reservationRepository.save(reservation);

        return new SuccessResponse<>(RESERVATION_MAKE_SUCCESS, NoneResponse.NONE);
    }

    /**
     * 임차인 예약 조회
     *
     * @return SuccessResponse - ReservationListResponse DTO
     * @return SuccessResponse - 예약 목록이 없을 경우, RESERVATION_LIST_EMPTY
     */
    @Transactional
    public SuccessResponse<ReservationListResponse> getMyReservationList(Long userId) {
        User user = userRepository.getById(userId);
        List<Reservation> reservations = reservationRepository.findByUserId(userId);
        if (reservations == null || reservations.isEmpty()) {
            return new SuccessResponse<>(RESERVATION_LIST_EMPTY, new ReservationListResponse(List.of()));
        }

        List<ReservationResponse> updatedReservations = new ArrayList<>();
        for (Reservation reservation : reservations) {
            boolean enstance = false;
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                Optional<Video> videoOptional = videoRepository.findByReservationId(reservation.getId());
                if (videoOptional.isPresent() && videoOptional.get().getVideoStatus() == VideoStatus.RECORDED) {
                    Reservation updatedReservation = reservation.completeStatus();
                    reservationRepository.save(updatedReservation);
                    reservation = updatedReservation;
                }

                Optional<Session> session = webrtcSessionService.getSession(reservation.getId());
                enstance = session.isPresent();
            }

            updatedReservations.add(ReservationResponse.from(reservation, enstance));
        }

        ReservationListResponse reservationListResponse = ReservationListResponse.from(updatedReservations);
        return new SuccessResponse<>(RESERVATION_LIST_SUCCESS, reservationListResponse);
    }

    /**
     * 임차인 예약 취소
     *
     * @param id 취소할 얘약 id
     * @return SuccessResponse
     * @throws AppException userid와 취소할 예약의 user id가 불일치시 ACCESS_DENIED 발생
     * @throws AppException 이미 취소된 예약인데 취소 시 RESERVATION_CANCELLED_DUPLICATED 발생
     * @throws AppException 이미 확정된 예약인데 취소 시 RESERVATION_CANCELLED_UNAVAILABLE 발생
     * @throws AppException 없는 예약 id라면, BAD_REQUEST 발생
     */
    @Transactional
    public SuccessResponse<NoneResponse> updateStatusReservation(Long userId, Long id) {
        Reservation reservation = vaildateReservation(id);
        if(reservation.getUser().getId()!=userId){
            throw new AppException(ACCESS_DENIED);
        }
        // 이미 취소된 예약인지 확인
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new AppException(RESERVATION_CANCELLED_DUPLICATED);
        }

        // 이미 확정된 예약인지 확인
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new AppException(RESERVATION_CANCELLED_UNAVAILABLE);
        }

        // 상태를 '예약취소'로 변경
        Reservation updatedReservation = reservation.withStatus();

        // 상태 변경된 예약 저장
        reservationRepository.save(updatedReservation);

        return new SuccessResponse<>(RESERVATION_UPDATE_STATUS_SUCCESS, NoneResponse.NONE);
    }

    private Reservation vaildateReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new AppException(BAD_REQUEST));
    }

    /**
     * 중개인 예약확정
     *
     * @param id 확정할 예약 id
     * @return SuccessResponse
     * @throws AppException agent id와 확정할 예약의 agent id가 불일치시 ACCESS_DENIED 발생
     * @throws AppException 이미 확정된 예약인데 확정 시 RESERVATION_CANCELLED_DUPLICATED 발생
     * @throws AppException 이미 취소된 예약인데 확정 시 RESERVATION_CONFIRMED_UNAVAILABLE 발생
     * @throws AppException 없는 예약 id라면, BAD_REQUEST 발생
     * @throws AppException 중개인이 동일매물, 같은 시간대 매물의 예약을 2개이상 확정 시 RESERVATION_CONFIRMED_DUPLICATED_TIME_ROOM 발생
     */
    @Transactional
    public SuccessResponse<NoneResponse> confirmStatusReservation(Long userId, Long id) {
        Reservation reservation = vaildateReservation(id);
        User user = userRepository.getById(userId);
        Agent agent = agentRepository.getByUserId(userId);
        Long agentId = agent.getId();

        if(reservation.getRoom().getAgent().getId()!=agentId){
            throw new AppException(ACCESS_DENIED);
        }
        // 취소된 예약이라면 확정할 수 없다.
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new AppException(RESERVATION_CONFIRMED_UNAVAILABLE);
        }

        // 이미 확정된 예약인지 확인
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new AppException(RESERVATION_CONFIRMED_DUPLICATED);
        }

        Optional<Reservation> existsConfirmedReservation = reservationRepository.findByRoomAndReservationTimeAndStatus(
                reservation.getRoom(), reservation.getReservationTime(), ReservationStatus.CONFIRMED);
        if (existsConfirmedReservation.isPresent()) {
            throw new AppException(RESERVATION_CONFIRMED_DUPLICATED_TIME_ROOM);
        }

        // 상태를 '예약확정'으로 변경
        Reservation confirmedReservation = reservation.confirmStatus();

        // 상태 변경된 예약 저장
        reservationRepository.save(confirmedReservation);

        return new SuccessResponse<>(RESERVATION_CONFIRM_SUCCESS, NoneResponse.NONE);
    }
    /**
     * 중개인 예약 목록 조회
     *
     * @return SuccessResponse - ReservationListResponse DTO
     * @return SuccessResponse - 예약 목록이 없을 경우, RESERVATION_LIST_EMPTY
     */
    @Transactional
    public SuccessResponse<ReservationListResponse> getAgentReservations(Long userId) {
        User user = userRepository.getById(userId);
        Agent agent = agentRepository.getByUserId(userId);
        Long agentId = agent.getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<Reservation> reservations = reservationRepository.findByRoomAgentIdAndReservationTimeAfterOrderByReservationTimeAscIdAsc(agentId, today);

        if (reservations.isEmpty()) {
            return new SuccessResponse<>(RESERVATION_LIST_EMPTY, new ReservationListResponse(List.of()));
        }

        List<ReservationResponse> updatedReservations = new ArrayList<>();
        for (Reservation reservation : reservations) {
            boolean enstance = reservation.getReservationTime().minusMinutes(10).isBefore(now);

            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                Optional<Video> videoOptional = videoRepository.findByReservationId(reservation.getId());
                if (videoOptional.isPresent() && videoOptional.get().getVideoStatus() == VideoStatus.RECORDED) {
                    Reservation updatedReservation = reservation.completeStatus();
                    updatedReservation = reservationRepository.save(updatedReservation);
                    updatedReservations.add(ReservationResponse.from(updatedReservation, enstance));
                } else {
                    updatedReservations.add(ReservationResponse.from(reservation, enstance));
                }
            } else {
                updatedReservations.add(ReservationResponse.from(reservation, enstance));
            }
        }

        ReservationListResponse reservationListResponse = ReservationListResponse.from(updatedReservations);
        return new SuccessResponse<>(RESERVATION_LIST_SUCCESS, reservationListResponse);
    }

    /**
     * 매물, 날짜별 예약가능한 시간 조회
     * 중개인 업무시간 내에서 예약 확정 시간대를 제외하고 반환
     *
     * @param request 매물과 날짜 DTO
     * @return  SuccessResponse - AvailableTimeResponse DTO
     * @throws AppException 매물을 못 찾을 시 ROOM_NOT_FOUND 발생
     * @throws AppException 중개인 업무시간을 못 찾을 시 WORKHOUR_NOT_FOUND 발생
     */
    @Override
    @Transactional(readOnly = true)
    public SuccessResponse<AvailableTimeResponse> getAvailableTimes(AvailableTimeRequest request) {
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new AppException(ROOM_NOT_FOUND));
        AgentWorkhour workhour = agentWorkhourRepository.findByAgent(room.getAgent())
                .orElseThrow(() -> new AppException(WORKHOUR_NOT_FOUND));
        LocalTime startTime = LocalTime.parse(workhour.getWeekdayStartTime());
        LocalTime endTime = LocalTime.parse(workhour.getWeekdayEndTime() );
        if (getDayCategory(request.date())=="WEEKEND"){
            startTime = LocalTime.parse(workhour.getWeekendStartTime());
            endTime = LocalTime.parse(workhour.getWeekendEndTime());
        }

        List<LocalTime> allPossibleTimes = generateTimeSlots(startTime, endTime);
        List<LocalTime> bookedTimes = reservationRepository.findConfirmedReservationTimes(request.roomId(), request.date());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        List<String> availableTimes = allPossibleTimes.stream()
                .filter(time -> !bookedTimes.contains(time))
                .map(time -> time.format(formatter))
                .collect(Collectors.toList());

        return new SuccessResponse<>(AVAILABLE_TIMES_RETRIEVED, new AvailableTimeResponse(availableTimes));
    }

    private String getDayCategory(LocalDate date) {
        // 날짜에 따라 주말, 주중인지 파악
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        switch (dayOfWeek) {
            case SATURDAY:
            case SUNDAY:
                return "WEEKEND";
            default:
                return "WEEKDAY";
        }
    }

    private List<LocalTime> generateTimeSlots(LocalTime start, LocalTime end) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime current = start;
        while (current.isBefore(end)) {
            slots.add(current);
            current = current.plusMinutes(30);
        }
        return slots;
    }

    /**
     * 중개인 예약 취소
     *
     * @param id 취소할 예약 id
     * @return SuccessResponse
     * @throws AppException agent id와 취소할 예약의 agent id가 불일치시 ACCESS_DENIED 발생
     * @throws AppException 이미 취소된 예약인데 취소 시 RESERVATION_CANCELLED_DUPLICATED 발생
     * @throws AppException 이미 확정된 예약인데 취소 시 RESERVATION_CANCELLED_UNAVAILABLE 발생
     * @throws AppException 없는 예약 id라면, BAD_REQUEST 발생
     */
    @Transactional
    public SuccessResponse<NoneResponse> deleteStatusReservation(Long userId, Long id) {
        Reservation reservation = vaildateReservation(id);
        User user = userRepository.getById(userId);
        Agent agent = agentRepository.getByUserId(userId);
        Long agentId = agent.getId();
        if(reservation.getRoom().getAgent().getId()!=agentId){
            throw new AppException(ACCESS_DENIED);
        }
        // 이미 취소된 예약인지 확인
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new AppException(RESERVATION_CANCELLED_DUPLICATED);
        }

        // 이미 확정된 예약인지 확인
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new AppException(RESERVATION_CANCELLED_UNAVAILABLE);
        }

        // 상태를 '예약취소'로 변경
        Reservation updatedReservation = reservation.withStatus();

        // 상태 변경된 예약 저장
        reservationRepository.save(updatedReservation);

        return new SuccessResponse<>(RESERVATION_UPDATE_STATUS_SUCCESS, NoneResponse.NONE);
    }

}
