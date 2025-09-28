# 🎵 Hướng dẫn thêm âm thanh cho Duck Race Game

## 📁 **Thư mục đặt file âm thanh:**

```
app/src/main/res/raw/
```

## 🎯 **Các file âm thanh cần thiết:**

### **1. Nhạc nền (Background Music)**

- **File**: `background_music.mp3`
- **Mô tả**: Nhạc nền vui vẻ, có thể loop
- **Kích thước**: 2-5MB
- **Thời lượng**: 30-60 giây (sẽ loop)

### **2. Âm thanh bắt đầu cuộc đua**

- **File**: `race_start.mp3`
- **Mô tả**: Âm thanh khi nhấn Start
- **Kích thước**: 100-500KB
- **Thời lượng**: 1-3 giây

### **3. Tiếng vịt kêu**

- **File**: `duck_quack.mp3`
- **Mô tả**: Tiếng vịt kêu khi boost
- **Kích thước**: 50-200KB
- **Thời lượng**: 0.5-1 giây

### **4. Âm thanh kết thúc**

- **File**: `race_finish.mp3`
- **Mô tả**: Âm thanh khi có người thắng
- **Kích thước**: 200-800KB
- **Thời lượng**: 2-5 giây

## 🎨 **Gợi ý nội dung âm thanh:**

### **Background Music:**

- Nhạc vui vẻ, sôi động
- Không có lời (instrumental)
- Tempo vừa phải (120-140 BPM)
- Có thể loop mượt mà

### **Sound Effects:**

- **Race Start**: Tiếng còi, chuông, hoặc fanfare
- **Duck Quack**: Tiếng vịt kêu tự nhiên
- **Race Finish**: Tiếng vỗ tay, chuông chiến thắng

## 📱 **Tối ưu cho Android:**

### **Format khuyến nghị:**

- **MP3**: Chất lượng tốt, kích thước nhỏ
- **OGG**: Tối ưu cho Android
- **WAV**: Chất lượng cao nhưng file lớn

### **Kích thước file:**

- **Tổng cộng**: < 20MB
- **Mỗi file**: < 5MB
- **Bitrate**: 128-192 kbps

## 🛠️ **Cách thêm file:**

1. **Copy file** vào `app/src/main/res/raw/`
2. **Đặt tên** theo convention: `tên_file.mp3`
3. **Build project** để kiểm tra
4. **Test** trên thiết bị

## 🎮 **Tính năng âm thanh đã tích hợp:**

- ✅ **Nhạc nền** - Tự động phát khi bắt đầu race
- ✅ **Âm thanh Start** - Khi nhấn nút Start
- ✅ **Tiếng vịt kêu** - Khi vịt được boost
- ✅ **Âm thanh kết thúc** - Khi có người thắng
- ✅ **Tự động dừng** - Khi reset hoặc kết thúc
- ✅ **Quản lý memory** - Tự động giải phóng khi đóng app

## 🚀 **Sau khi thêm file âm thanh:**

1. **Build project**: `./gradlew build`
2. **Test trên thiết bị** hoặc emulator
3. **Điều chỉnh âm lượng** nếu cần trong code

## 💡 **Lưu ý:**

- Nếu không có file âm thanh, game vẫn chạy bình thường
- Âm thanh sẽ tự động phát theo sự kiện
- Có thể tắt/bật âm thanh bằng cách comment code
- File âm thanh phải có tên không chứa ký tự đặc biệt

**Chúc bạn có trải nghiệm âm thanh tuyệt vời! 🎵✨**
