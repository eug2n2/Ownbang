import React from "react";
import {
  TableRow,
  TableCell,
  Avatar,
  Typography,
  Box,
  Button,
} from "@mui/material";
import {
  CheckCircleOutlined,
  CancelOutlined,
  PendingOutlined,
  HistoryOutlined,
} from "@mui/icons-material";
import { useMediaQuery, useTheme } from "@mui/material";

export default function AgentReservationItem({
  reservation,
  confirmReservation,
  denyReservation,
}) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  const userInfo = reservation.userReservationInfoResponse;

  console.log(reservation);
  if (!userInfo) {
    return null;
  }

  const formatPhoneNumber = (phoneNumber) => {
    if (phoneNumber.length === 11) {
      return `${phoneNumber.slice(0, 3)}-${phoneNumber.slice(
        3,
        7
      )}-${phoneNumber.slice(7)}`;
    } else if (phoneNumber.length === 10) {
      return `${phoneNumber.slice(0, 3)}-${phoneNumber.slice(
        3,
        6
      )}-${phoneNumber.slice(6)}`;
    }
    return phoneNumber;
  };

  const getStatusIconAndText = (status) => {
    switch (status) {
      case "CONFIRMED":
        return {
          icon: <CheckCircleOutlined style={{ color: "green" }} />,
          text: "예약 확정",
        };
      case "APPLYED":
        return {
          icon: <PendingOutlined style={{ color: "orange" }} />,
          text: "대기 중",
        };
      case "CANCELLED":
        return {
          icon: <CancelOutlined style={{ color: "red" }} />,
          text: "예약 취소",
        };
      case "COMPLETED":
        return {
          icon: <HistoryOutlined style={{ color: "blue" }} />,
          text: "통화 완료",
        };
      default:
        return {
          icon: null,
          text: "알 수 없음",
        };
    }
  };

  const { icon, text } = getStatusIconAndText(reservation.status);

  // 예약 날짜 데이터
  let reservationDate = new Date(
    reservation.reservationTime
  ).toLocaleDateString("ko-KR");

  if (reservationDate.endsWith(".")) {
    reservationDate = reservationDate.slice(0, -1);
  }

  // 예약 시간 데이터
  const reservationTime = new Date(
    reservation.reservationTime
  ).toLocaleTimeString("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
  });

  // 예약 확정 핸들러
  const onConfirm = async () => {
    try {
      await confirmReservation(reservation.id);
      console.log(`예약아이디 ${reservation.id}가 확정되었습니다.`);
    } catch (error) {
      console.error("Failed to confirm reservation:", error);
    }
  };

  // 예약 거절 핸들러
  const onDeny = async () => {
    try {
      await denyReservation(reservation.id);
      console.log(`예약아이디 ${reservation.id}가 거절되었습니다.`);
    } catch (error) {
      console.error("Failed to confirm reservation:", error);
    }
  };

  const isApplied = reservation.status === "APPLYED";

  return (
    <TableRow>
      {!isMobile && (
        <TableCell sx={{ padding: "4px 8px" }}>
          <Avatar
            src={reservation.roomProfileImage}
            variant="rounded"
            sx={{ width: "150px", height: "90px" }}
          />
        </TableCell>
      )}
      <TableCell>
        <Typography variant={isMobile ? "body2" : "body1"}>
          {reservationDate}
        </Typography>
      </TableCell>
      <TableCell>
        <Typography variant={isMobile ? "body2" : "body1"}>
          {reservationTime}
        </Typography>
      </TableCell>
      {!isMobile && (
        <TableCell>
          <Typography variant={"body1"}>{userInfo.userName}</Typography>
        </TableCell>
      )}
      <TableCell>
        <Box sx={{ display: "flex", alignItems: "center" }}>
          {icon}
          <Typography sx={{ ml: 1, whiteSpace: "nowrap" }} variant={"body1"}>
            {text}
          </Typography>
        </Box>
      </TableCell>
      <TableCell>
        <Button
          color="primary"
          onClick={onConfirm}
          disabled={!isApplied} // Applyed 상태일 때만 활성화
          sx={{
            px: 0,
            py: 0,
            minWidth: "auto",
            fontSize: "0.875rem",
            color: theme.palette.success.main,
          }}
        >
          확정
        </Button>
        {" / "}
        <Button
          color="primary"
          onClick={onDeny}
          disabled={!isApplied} // Applyed 상태일 때만 활성화
          sx={{
            px: 0,
            py: 0,
            minWidth: "auto",
            fontSize: "0.875rem",
            color: theme.palette.warning.main,
          }}
        >
          거절
        </Button>
      </TableCell>
      <TableCell>
        <Typography variant={"body1"}>
          {formatPhoneNumber(userInfo.phoneNumber)}
        </Typography>
      </TableCell>
      <TableCell>
        <Button variant="contained" color="primary">
          입장하기
        </Button>
      </TableCell>
    </TableRow>
  );
}
