RR-Interval 기반 필터링
원리:바로 이전의 심박 간격(RR-Interval)에 비해 다음 간격이 갑자기 2배 이상 길어지거나, 1/2 이하로 짧아지는 것은 생리학적으로 거의 불가능함을 이용, 이는 R-peak를 놓쳤거나(missed beat) 노이즈를 R-peak로 잘못 감지(false detection)했을 확률이 99%임.
◦구현:
a.R-peak가 감지될 때마다, 이전 R-peak와의 시간 간격(current_RR)을 계산함.
b.이전의 정상적인 간격(last_valid_RR)과 비교합니다.
c.만약 current_RR이 last_valid_RR * 0.7 ~ last_valid_RR * 1.3 범위를 벗어나면, 해당 current_RR로 계산된 BPM을 "의심스러운 값"으로 간주하고 버립니다.
d.정상 범위 내에 있을 때만 last_valid_RR을 갱신하고, 이 값으로 계산된 BPM을 신뢰합니다.
e.신뢰한 BPM만을 블루투스로 전송함.
