#include <SoftwareSerial.h>

// === 핀 설정 ===
SoftwareSerial bluetooth(3, 4); // RX, TX

// AD8232 ECG 센서 핀 설정
const int ecgOutputPin = A0;
const int ecgLoPlusPin = 10;
const int ecgLoMinusPin = 11;

// [변경됨] 진동 모터 핀 설정 (강도 조절을 위해 PWM 핀인 6번 사용)
const int motorPin = 6;       

// [설정] 진동 강도와 설렘 기준 BPM
const int motorIntensity = 70; // 진동 세기 (0~255). 255가 최대, 100은 약하게.
const int flutterBpm = 85;      // 설렘을 느끼는 기준 BPM (보통 85~90 정도)

int bpm = 0;
int signal;
unsigned long lastBeatTime = 0;
boolean pulse = false;
boolean qrs = false;

int peakThreshold = 512;

void setup() {
  Serial.begin(9600);
  bluetooth.begin(9600);

  pinMode(ecgLoPlusPin, INPUT);
  pinMode(ecgLoMinusPin, INPUT);

  // 모터 핀 설정
  pinMode(motorPin, OUTPUT);
  analogWrite(motorPin, 0); // 초기 상태는 0(꺼짐)

  Serial.println("아두이노 ECG 측정 준비 완료 ");
  Serial.println("---------------------------------");
}

void loop() {
  boolean electrodesOff = (digitalRead(ecgLoPlusPin) == 1) || (digitalRead(ecgLoMinusPin) == 1);

  if (electrodesOff) {
    analogWrite(motorPin, 0); // 전극 떨어지면 모터 끄기
  } 
  else {
    signal = analogRead(ecgOutputPin);

    // R-peak 감지 로직
    if (signal > peakThreshold && !qrs) {
      pulse = true;
    }
    
    if (pulse && !qrs) {
      qrs = true;
      
      unsigned long currentTime = millis();
      unsigned long ibis = currentTime - lastBeatTime;
      lastBeatTime = currentTime;

      bpm = 60000 / ibis;

      if (bpm < 40 || bpm > 200) {
        bpm = 0;
      }
    }
    
    if (signal < (peakThreshold - 50) && qrs) {
      pulse = false;
      qrs = false;
    }

    // [수정됨] 설렘 BPM(85) 이상일 때 '약한 진동' 작동
    if (bpm >= flutterBpm) {
      // digitalWrite 대신 analogWrite 사용 (강도 조절)
      analogWrite(motorPin, motorIntensity); 
    } else {
      analogWrite(motorPin, 0);  // 진동 끄기
    }
  }

  // 2초 이상 심박 없으면 리셋
  if (millis() - lastBeatTime > 2000) {
    bpm = 0;
    analogWrite(motorPin, 0); 
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
      
      if (bpm >= flutterBpm) {
        Serial.println(" [진동모터 작동]");
      } else {
        Serial.println("");
      }
      
      bluetooth.println(bpm);
    }
  }
  
  delay(1);
}