import React, { useState } from "react";
import {
  Stack,
  Autocomplete,
  TextField,
  Box,
  Button,
  IconButton,
} from "@mui/material";
import { useTheme } from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";

const CheckListTitle = ({
  checklist,
  setSelectedTitle,
  addTemplate,
  deleteTemplate,
}) => {
  const theme = useTheme();
  const [newTemplateTitle, setNewTemplateTitle] = useState("");

  const options = checklist
    ? checklist.map((el) => ({
        checklistId: el.checklistId,
        title: el.title,
      }))
    : [];

  const handleTemplateChange = (event, value) => {
    if (value && typeof value === "string") {
      setNewTemplateTitle(value);
    } else if (value && value.title) {
      setSelectedTitle(value.title);
      setNewTemplateTitle("");
    } else {
      setSelectedTitle("");
    }
  };

  const handleAddTemplate = async () => {
    if (newTemplateTitle.trim() !== "") {
      const newChecklist = await addTemplate(newTemplateTitle);
      setSelectedTitle(newTemplateTitle);
      setNewTemplateTitle(newTemplateTitle);
    }
  };

  const handleDeleteTemplate = async (event, checklistId) => {
    event.stopPropagation();
    await deleteTemplate(checklistId);
    setSelectedTitle(""); // 선택된 템플릿 초기화
  };

  return (
    <Stack spacing={2} sx={{ width: "100%" }}>
      <Box sx={{ display: "flex", alignItems: "end" }}>
        <Autocomplete
          freeSolo
          id="templateChoice"
          options={options}
          getOptionLabel={(option) => option.title}
          onInputChange={(event, value) => {
            setNewTemplateTitle(value);
            if (!value) {
              setSelectedTitle("");
            }
          }}
          onChange={handleTemplateChange}
          inputValue={newTemplateTitle}
          renderInput={(params) => (
            <TextField
              {...params}
              variant="standard"
              label="탬플릿"
              placeholder="사용할 템플릿을 선택하거나 추가하세요"
              InputProps={{
                ...params.InputProps,
                sx: {
                  padding: "10px",
                  fontSize: "12px",
                },
              }}
              sx={{
                "& .MuiInputBase-root": {
                  padding: "10px",
                  fontSize: "12px",
                },
              }}
            />
          )}
          renderOption={(props, option) => (
            <li
              {...props}
              key={option.checklistId}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              {option.title}
              <IconButton
                edge="end"
                aria-label="delete"
                onClick={(event) =>
                  handleDeleteTemplate(event, option.checklistId)
                }
              >
                <DeleteIcon />
              </IconButton>
            </li>
          )}
          sx={{ flexGrow: 1 }}
        />
        <Button onClick={handleAddTemplate} variant="contained" sx={{ ml: 1 }}>
          추가
        </Button>
      </Box>
    </Stack>
  );
};

export default CheckListTitle;
