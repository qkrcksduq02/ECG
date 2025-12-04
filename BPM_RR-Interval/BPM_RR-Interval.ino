#include <SoftwareSerial.h>

// === 핀 설정 ===
SoftwareSerial bluetooth(3, 4); // RX, TX

// AD8232 ECG 센서 핀 설정
const int ecgOutputPin = A0;
const int ecgLoPlusPin = 10;
const int ecgLoMinusPin = 11;

// 진동 모터 핀 설정
const int motorPin = 6;

// 진동 강도 설정
const int motorIntensity = 100; // 진동 세기 (0~255)

// --- 변수 설정 ---
int peakBpm = 0;
int bpm = 0;
int signal;
unsigned long lastBeatTime = 0;
boolean pulse = false;
boolean qrs = false;
int peakThreshold = 512;

// --- [새로운 기능] RR-Interval 필터링을 위한 변수 ---
unsigned long lastValid_RR = 800; // 가장 최근에 검증된 정상 심박 간격 (초기값: 75bpm에 해당하는 800ms)
const float RR_TOLERANCE_LOW = 0.7;  // 허용 오차 범위 (최소 70%)
const float RR_TOLERANCE_HIGH = 1.3; // 허용 오차 범위 (최대 130%)

// --- 초기 안정화 시간을 위한 변수 ---
unsigned long connectionTime = 0;
const int INITIAL_DELAY_MS = 5000;  // 5초의 초기 지연 시간

void setup() {
  Serial.begin(9600);
  bluetooth.begin(9600);

  pinMode(ecgLoPlusPin, INPUT);
  pinMode(ecgLoMinusPin, INPUT);
  pinMode(motorPin, OUTPUT);
  analogWrite(motorPin, 0);

  Serial.println("아두이노 ECG 측정 준비 완료 (RR-Interval 필터 적용)");
  Serial.println("연결 안정화를 위해 5초간 전송을 지연합니다...");
  Serial.println("---------------------------------");

  // 부팅 시 현재 시간을 기록하여 5초 지연 타이머 시작
  connectionTime = millis();
}

void loop() {
  // --- 블루투스 'V' 수신 시 즉시 진동 ---
  if (bluetooth.available()) {
    char receivedChar = bluetooth.read();
    if (receivedChar == 'V') {
      Serial.println("\n=================================");
      Serial.println("'V' 신호 수신! -> 즉시 진동 실행");
      String peakBpmMessage = "최고 BPM: " + String(peakBpm);
      Serial.println(peakBpmMessage);
      bluetooth.println(peakBpmMessage);
      Serial.println("=================================\n");
      analogWrite(motorPin, motorIntensity); delay(150);
      analogWrite(motorPin, 0); delay(100);
      analogWrite(motorPin, motorIntensity); delay(150);
      analogWrite(motorPin, 0);
    }
  }

  // --- ECG 측정 로직 ---
  boolean electrodesOff = (digitalRead(ecgLoPlusPin) == 1) || (digitalRead(ecgLoMinusPin) == 1);

  if (electrodesOff) {
    bpm = 0;
    peakBpm = 0;
    // 재연결 시 다시 5초 지연 및 필터 초기화
    connectionTime = millis();
    lastValid_RR = 800; // 필터 기준값 초기화
  } else {
    signal = analogRead(ecgOutputPin);
    if (signal > peakThreshold && !qrs) {
      pulse = true;
    }

    // R-peak 감지 시
    if (pulse && !qrs) {
      qrs = true;
      unsigned long currentTime = millis();
      unsigned long ibis = currentTime - lastBeatTime; // 현재 심박 간격(RR-Interval) 계산

      // --- [수정됨] RR-Interval 기반 필터링 로직 ---
      // 현재 간격(ibis)이, 마지막으로 검증된 간격(lastValid_RR)의 70% ~ 130% 범위 내에 있을 때만 정상으로 판단
      if (ibis > (lastValid_RR * RR_TOLERANCE_LOW) && ibis < (lastValid_RR * RR_TOLERANCE_HIGH)) {
        // 정상 판정!
        lastValid_RR = ibis; // 이 값을 새로운 '검증된 간격'으로 업데이트
        bpm = 60000 / lastValid_RR; // 이 간격으로 BPM 계산

        // 1차 필터링: 생리학적 한계치를 벗어나는 값은 무시
        if (bpm < 40 || bpm > 200) {
            bpm = 0;
        }

        // 5초 지연 후 최고 BPM 값 업데이트
        if (millis() > connectionTime + INITIAL_DELAY_MS) {
          if (bpm > peakBpm) {
            peakBpm = bpm;
          }
        }
        
      } else {
        // 비정상 판정!
        // BPM 값을 이전 상태로 유지하고, lastValid_RR은 갱신하지 않음.
        // 따라서 시스템은 이 비정상 측정값에 전혀 영향을 받지 않음.
        Serial.println(">> 비정상 RR-Interval 감지! 이전 값 유지.");
      }
      
      lastBeatTime = currentTime; // 다음 간격 계산을 위해 현재 시간 기록
    }

    if (signal < (peakThreshold - 50) && qrs) {
      pulse = false;
      qrs = false;
    }
  }

  if (millis() - lastBeatTime > 2000) {
    bpm = 0;
  }

  // === 데이터 전송 ===
  static unsigned long lastSendTime = 0;
  if (millis() - lastSendTime > 1000) {
    lastSendTime = millis();

    if (electrodesOff) {
      String msg = "! 전극 확인 필요";
      Serial.println(msg);
      bluetooth.println(msg);
    } else {
      Serial.print("Current BPM: ");
      Serial.print(bpm);
      Serial.print(" (Peak: ");
      Serial.print(peakBpm);
      Serial.print(", RR: ");
      Serial.print(lastValid_RR);
      Serial.print("ms)");

      // 5초 지연 후 블루투스 전송
      if (millis() > connectionTime + INITIAL_DELAY_MS) {
        if (bpm > 0) { // BPM이 유효할 때만 전송
            Serial.println(" -> BT 전송");
            bluetooth.println(bpm);
        } else {
            Serial.println(" -> (유효 BPM 없음)");
        }
      } else {
        Serial.println(" -> (안정화 대기중)");
      }
    }
  }
  delay(1);
}
