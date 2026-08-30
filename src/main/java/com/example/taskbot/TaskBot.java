package com.example.taskbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskBot extends TelegramLongPollingBot {

    private final TaskRepository repository;

    // Временное хранение процесса создания/редактирования задачи
    private final Map<Long, String> userState = new ConcurrentHashMap<>();
    private final Map<Long, String> tempTaskText = new ConcurrentHashMap<>();
    private final Map<Long, String> tempTaskPriority = new ConcurrentHashMap<>();
    private final Map<Long, Long> editingTaskId = new ConcurrentHashMap<>();

    @Value("${bot.name}")
    private String botName;

    public TaskBot(@Value("${bot.token}") String token, TaskRepository repository) {
        super(token);
        this.repository = repository;
    }

    @Override
    public String getBotUsername() { return botName; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.hasMessage() && update.getMessage().hasText()) {
                handleText(update);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleText(Update update) throws Exception {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        if ("/start".equals(text) || "Отмена".equals(text)) {
            userState.remove(chatId);
            sendMenu(chatId, "Главное меню. Выбери действие:");
            return;
        }

        if ("📝 Новая задача".equals(text)) {
            userState.put(chatId, "WAITING_FOR_TEXT");
            execute(new SendMessage(chatId.toString(), "Напиши текст задачи:"));
            return;
        }

        if ("📋 Мои задачи".equals(text)) {
            // Сортировка по дате и времени (ближайшие сверху)
            List<Task> tasks = repository.findByChatIdAndStatusOrderByDeadlineAsc(chatId, "ACTIVE");
            if (tasks.isEmpty()) {
                execute(new SendMessage(chatId.toString(), "У тебя нет активных задач."));
                return;
            }
            for (Task task : tasks) {
                sendTaskMessage(chatId, task);
            }
            return;
        }

        String state = userState.get(chatId);

        // Шаг 1: Получили текст, просим выбрать приоритет
        if ("WAITING_FOR_TEXT".equals(state)) {
            tempTaskText.put(chatId, text);
            userState.put(chatId, "WAITING_FOR_PRIORITY");
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Выбери приоритет задачи:")
                    .replyMarkup(createPriorityKeyboard())
                    .build());
            return;
        }

        // Шаг 3: Получили дедлайн текстом (например, "30.08.2026 18:00") и сохраняем
        if ("WAITING_FOR_DEADLINE".equals(state)) {
            LocalDateTime deadline;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                deadline = LocalDateTime.parse(text, formatter);
            } catch (Exception e) {
                execute(new SendMessage(chatId.toString(), "⚠️ Неверный формат! Введи дату и время строго в формате: ДД.ММ.ГГГГ ЧЧ:ММ (например, 30.08.2026 15:00)"));
                return;
            }

            Task task = new Task(chatId, tempTaskText.get(chatId), tempTaskPriority.get(chatId), deadline, "ACTIVE");
            repository.save(task);

            userState.remove(chatId);
            sendMenu(chatId, "✅ Задача с дедлайном и приоритетом успешно создана!");
            return;
        }

        // Шаг редактирования текста существующей задачи
        if ("WAITING_FOR_EDIT_TEXT".equals(state)) {
            Long taskId = editingTaskId.get(chatId);
            repository.findById(taskId).ifPresent(task -> {
                task.setText(text);
                repository.save(task);
            });
            userState.remove(chatId);
            editingTaskId.remove(chatId);
            sendMenu(chatId, "✏️ Задача успешно обновлена!");
            return;
        }
    }

    private void handleCallback(Update update) throws Exception {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer msgId = update.getCallbackQuery().getMessage().getMessageId();

        // Обработка выбора приоритета кнопками
        if (data.startsWith("PRIORITY_")) {
            String priority = data.replace("PRIORITY_", "");
            tempTaskPriority.put(chatId, priority);
            userState.put(chatId, "WAITING_FOR_DEADLINE");

            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(msgId);
            edit.setText("Приоритет выбран: " + priority + "\n\nТеперь введи дедлайн (дата и время в формате ДД.ММ.ГГГГ ЧЧ:ММ):");
            execute(edit);
            return;
        }

        String[] parts = data.split("_");
        String action = parts[0];
        Long taskId = Long.parseLong(parts[1]);

        if ("DONE".equals(action)) {
            repository.findById(taskId).ifPresent(task -> {
                task.setStatus("COMPLETED");
                task.setCompletedAt(LocalDateTime.now());
                repository.save(task);
            });
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(msgId);
            edit.setText("✅ ~Выполнено~");
            execute(edit);
        }
        else if ("EDIT".equals(action)) {
            userState.put(chatId, "WAITING_FOR_EDIT_TEXT");
            editingTaskId.put(chatId, taskId);
            execute(new SendMessage(chatId.toString(), "Напиши новый текст для этой задачи:"));
        }
        else if ("DEL".equals(action)) {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(msgId);
            edit.setText("Точно удалить задачу?");
            edit.setReplyMarkup(createConfirmKeyboard(taskId));
            execute(edit);
        }
        else if ("CONFIRM".equals(action)) {
            repository.deleteById(taskId);
            execute(new DeleteMessage(chatId.toString(), msgId));
        }
        else if ("CANCEL".equals(action)) {
            repository.findById(taskId).ifPresent(task -> {
                try {
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(msgId);
                    edit.setText(formatTaskText(task));
                    edit.setReplyMarkup(createTaskKeyboard(taskId));
                    execute(edit);
                } catch (Exception e) {}
            });
        }
    }

    private String formatTaskText(Task task) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy в HH:mm");
        String dateStr = task.getDeadline() != null ? task.getDeadline().format(formatter) : "Без дедлайна";
        return task.getPriority() + " | 📌 " + task.getText() + "\n⏰ Дедлайн: " + dateStr;
    }

    private void sendMenu(Long chatId, String text) throws Exception {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add("📝 Новая задача");
        row.add("📋 Мои задачи");
        keyboard.setKeyboard(List.of(row));
        msg.setReplyMarkup(keyboard);
        execute(msg);
    }

    private void sendTaskMessage(Long chatId, Task task) throws Exception {
        SendMessage msg = new SendMessage(chatId.toString(), formatTaskText(task));
        msg.setReplyMarkup(createTaskKeyboard(task.getId()));
        execute(msg);
    }

    private InlineKeyboardMarkup createPriorityKeyboard() {
        InlineKeyboardButton p1 = new InlineKeyboardButton("🔴 Высокий");
        p1.setCallbackData("PRIORITY_🔴 ВЫСОКИЙ");
        InlineKeyboardButton p2 = new InlineKeyboardButton("🟡 Средний");
        p2.setCallbackData("PRIORITY_🟡 СРЕДНИЙ");
        InlineKeyboardButton p3 = new InlineKeyboardButton("🟢 Низкий");
        p3.setCallbackData("PRIORITY_🟢 НИЗКИЙ");
        return new InlineKeyboardMarkup(List.of(List.of(p1, p2, p3)));
    }

    private InlineKeyboardMarkup createTaskKeyboard(Long taskId) {
        InlineKeyboardButton btnDone = new InlineKeyboardButton("✅ Готово");
        btnDone.setCallbackData("DONE_" + taskId);
        InlineKeyboardButton btnEdit = new InlineKeyboardButton("✏️ Редактировать");
        btnEdit.setCallbackData("EDIT_" + taskId);
        InlineKeyboardButton btnDel = new InlineKeyboardButton("🗑 Удалить");
        btnDel.setCallbackData("DEL_" + taskId);
        return new InlineKeyboardMarkup(List.of(List.of(btnDone, btnEdit), List.of(btnDel)));
    }

    private InlineKeyboardMarkup createConfirmKeyboard(Long taskId) {
        InlineKeyboardButton btnYes = new InlineKeyboardButton("⚠️ Уверен");
        btnYes.setCallbackData("CONFIRM_" + taskId);
        InlineKeyboardButton btnNo = new InlineKeyboardButton("❌ Отмена");
        btnNo.setCallbackData("CANCEL_" + taskId);
        return new InlineKeyboardMarkup(List.of(List.of(btnYes, btnNo)));
    }
}