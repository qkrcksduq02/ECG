
#include <SoftwareSerial.h>

// === 핀 설정 ===
// 블루투스 모듈을 위한 가상 시리얼 포트 설정 (RX, TX)
SoftwareSerial bluetooth(3, 4);

// AD8232 ECG 센서 핀 설정
const int ecgOutputPin = A0;  // 센서의 아날로그 신호 출력 핀
const int ecgLoPlusPin = 10;  // 전극 탈락 감지 (+)
const int ecgLoMinusPin = 11; // 전극 탈락 감지 (-)


int bpm = 0;                  // 계산된 BPM 값
int signal;                   // 현재 ECG 신호 값
unsigned long lastBeatTime = 0;   // 마지막 심박이 감지된 시간
boolean pulse = false;            // 피크의 시작을 감지하는 플래그
boolean qrs = false;              // QRS 파형이 진행 중인지 감지하는 플래그 (중복 계산 방지)


int peakThreshold = 512;


void setup() {
  // PC와 디버깅을 위한 시리얼 통신 시작
  Serial.begin(9600);
  // 블루투스 모듈과 통신 시작
  bluetooth.begin(9600);

  // 전극 탈락 감지 핀을 입력으로 설정
  pinMode(ecgLoPlusPin, INPUT);
  pinMode(ecgLoMinusPin, INPUT);

  Serial.println("아두이노 ECG 측정 준비 완료 (초기 로직 버전).");
  Serial.println("---------------------------------");
}

void loop() {
  // 전극이 정상적으로 부착되었는지 먼저 확인합니다.
  boolean electrodesOff = (digitalRead(ecgLoPlusPin) == 1) || (digitalRead(ecgLoMinusPin) == 1);

  if (!electrodesOff) {
    // 전극이 부착된 경우, 측정을 진행합니다.
    signal = analogRead(ecgOutputPin);

    // R-peak 감지 로직 (사용자가 요청한 초기 버전)
    // 1. 신호가 임계값을 넘고, 아직 QRS 파형이 진행 중이지 않을 때
    if (signal > peakThreshold && !qrs) {
      pulse = true;
    }
    
    // 2. 피크가 시작되었고(pulse), 아직 QRS로 처리되지 않았을 때
    if (pulse && !qrs) {
      qrs = true; // QRS 파형이 감지되었음을 표시 (중복 계산 방지)
      
      unsigned long currentTime = millis();
      unsigned long ibis = currentTime - lastBeatTime; // 심박 간 간격 (ms)
      lastBeatTime = currentTime;

      // BPM 계산: 60,000ms (1분) / 심박 간 간격
      bpm = 60000 / ibis;

      // 비정상적인 BPM 값 필터링
      if (bpm < 40 || bpm > 200) {
        bpm = 0;
      }
    }
    
    // 3. 신호가 임계값 아래로 내려가고, QRS 파형이 끝났다고 판단되면
    if (signal < (peakThreshold - 50) && qrs) {
      // 다음 심박을 감지할 수 있도록 플래그 초기화
      pulse = false;
      qrs = false;
    }
  }

  // 만약 2초 이상 새로운 심박이 감지되지 않으면, BPM을 0으로 리셋
  if (millis() - lastBeatTime > 2000) {
    bpm = 0;
  }
  
  // === 데이터 전송 ===
  // 1초에 한 번씩 앱으로 데이터 전송
  static unsigned long lastSendTime = 0;
  if (millis() - lastSendTime > 1000) {
    lastSendTime = millis();

    // 전극 상태 또는 BPM 값 중 하나를 선택하여 전송
    if (electrodesOff) {
      // 전극이 떨어진 경우, 상태 메시지 전송
      String msg = "! 전극 확인 필요";
      Serial.println(msg);      // PC 모니터에도 출력
      bluetooth.println(msg);   // 앱으로 전송
    } else {
      // 전극이 정상인 경우, 계산된 BPM 전송
      Serial.print("Current BPM: ");
      Serial.println(bpm);      // PC 모니터에도 출력
      bluetooth.println(bpm);   // 앱으로 전송
    }
  }
  
  delay(1); // 시스템 안정성을 위한 짧은 딜레이
}