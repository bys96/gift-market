package com.giftmarket.product.seed;

import com.giftmarket.global.storage.provider.StorageProvider;
import com.giftmarket.product.dto.request.ProductCreateRequest;
import com.giftmarket.product.dto.request.ProductOptionGroupRequest;
import com.giftmarket.product.dto.request.ProductOptionReferenceRequest;
import com.giftmarket.product.dto.request.ProductOptionUpdateRequest;
import com.giftmarket.product.dto.request.ProductOptionValueRequest;
import com.giftmarket.product.dto.request.ProductRegistrationRequest;
import com.giftmarket.product.dto.request.ProductRegistrationVariantRequest;
import com.giftmarket.product.entity.Category;
import com.giftmarket.product.entity.GiftOccasion;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.product.repository.CategoryRepository;
import com.giftmarket.product.repository.GiftOccasionRepository;
import com.giftmarket.product.repository.ProductVariantRepository;
import com.giftmarket.product.service.ProductRegistrationService;
import com.giftmarket.seller.entity.Seller;
import com.giftmarket.seller.entity.SellerStatus;
import com.giftmarket.seller.repository.SellerRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SampleProductSeedService {

    public static final String SEED_ID = "GM-SAMPLE-50-V1";

    private static final String SKU_PREFIX = SEED_ID + "-";
    private static final int PRODUCT_COUNT = 50;

    private static final OptionTemplate SMALL_LARGE = option("크기", "스탠다드", "라지", 4_000L);
    private static final OptionTemplate BASIC_GIFT = option("포장", "기본 포장", "메시지 카드 포장", 2_000L);
    private static final OptionTemplate CREAM_SAGE = option("색상", "크림", "세이지", 0L);
    private static final OptionTemplate TWO_SCENTS = option("향", "코튼 브리즈", "포레스트 우드", 0L);

    private static final List<ProductTemplate> PRODUCT_TEMPLATES = List.of(
            product("오후의 티 블렌드 컬렉션", "소담연구소", "향이 다른 잎차를 차분하게 즐기는 티 컬렉션", "캐모마일, 루이보스, 얼그레이를 각각 밀봉해 신선한 향을 오래 유지합니다.", 18_900L, "식품/커피·차", option("구성", "틴 3종", "틴 5종", 9_000L), "생일", "감사", "가벼운 선물"),
            product("싱글 오리진 드립백 12개입", "오후의정원", "산지별 개성을 간편하게 비교하는 드립백 커피", "중배전 원두를 한 잔 분량씩 질소 충전 포장해 사무실과 여행지에서도 깔끔하게 즐길 수 있습니다.", 14_500L, "식품/커피·차", option("구성", "12개입", "24개입", 12_000L), "감사", "응원", "가벼운 선물"),
            product("버터 풍미 구움과자 박스", null, "마들렌과 피낭시에를 담은 촉촉한 디저트 박스", "버터의 고소함을 살린 네 가지 맛을 개별 포장해 나눠 먹기 좋게 구성했습니다.", 22_000L, "식품/베이커리·떡", option("구성", "8개입", "12개입", 9_000L), "생일", "감사", "축하"),
            product("하루견과 20봉 선물박스", "모먼트메이드", "견과와 건과일을 하루 분량씩 담은 실속형 선물", "아몬드, 호두, 캐슈너트와 건크랜베리를 균형 있게 담아 휴대와 보관이 편리합니다.", 16_800L, "식품/건강식품", option("구성", "20봉", "30봉", 7_000L), "감사", "응원"),
            product("과일 수제청 듀오", "포근한결", "차와 에이드로 즐기는 상큼한 수제 과일청", "레몬과 자몽 과육을 넉넉히 담아 따뜻한 차부터 탄산 에이드까지 다양하게 활용할 수 있습니다.", 27_900L, "식품/음료", option("맛 구성", "레몬·자몽", "딸기·패션후르츠", 0L), "감사", "집들이", "기념일"),
            product("홈가드닝 허브 스타터 키트", null, "창가에서 시작하는 작은 허브 정원", "바질과 로즈메리 씨앗, 배양토, 화분, 이름표를 한 상자에 담아 처음 키우는 분도 쉽게 시작할 수 있습니다.", 12_900L, "리빙/꽃·식물", option("허브", "바질", "로즈메리", 0L), "집들이", "가벼운 선물"),
            product("클라우드 쿠션 홈 슬리퍼", "마음한칸", "폭신한 쿠션감과 가벼운 착화감의 실내화", "발바닥을 편안하게 받치는 이중 쿠션과 미끄럼 방지 밑창을 적용했습니다.", 19_800L, "패션/신발", option("색상", "오프화이트", "차콜", 0L), "생일", "집들이", "가벼운 선물"),
            product("무광 머그 앤 우드 코스터", "소담연구소", "담백한 무광 머그와 원목 코스터 구성", "손에 편안하게 잡히는 350ml 머그와 방수 마감한 원목 코스터를 함께 구성했습니다.", 24_500L, "리빙/주방용품", option("색상", "아이보리", "딥그린", 0L), "생일", "집들이", "가벼운 선물"),
            product("아로마 배스 솔트 컬렉션", null, "하루의 긴장을 풀어 주는 세 가지 입욕 향", "미네랄 솔트에 라벤더, 유칼립투스, 시트러스 향을 더해 취향에 따라 골라 사용할 수 있습니다.", 21_900L, "뷰티/바디케어", option("구성", "3병", "5병", 10_000L), "생일", "응원", "기념일"),
            product("세 가지 향 소이 캔들", "다정상점", "공간에 잔잔한 분위기를 더하는 소이 캔들", "식물성 소이 왁스와 코튼 심지를 사용했으며 작은 공간에 어울리는 향 세 가지를 담았습니다.", 25_000L, "리빙/캔들·디퓨저", TWO_SCENTS, "생일", "집들이", "기념일"),
            product("시어버터 핸드워시·크림 듀오", "모먼트메이드", "씻은 뒤에도 편안한 보습감을 남기는 핸드케어", "시어버터와 판테놀을 배합한 핸드워시와 크림으로 건조한 계절에 매일 사용하기 좋습니다.", 23_900L, "뷰티/바디케어", option("향", "무향", "화이트 티", 0L), "감사", "응원", "가벼운 선물"),
            product("데일리 모이스처 립밤 듀오", null, "휴대하기 좋은 고보습 립밤 두 개 구성", "끈적임을 줄인 밤 타입 제형으로 입술에 부드럽게 녹아들며 데일리 케어에 적합합니다.", 13_500L, "뷰티/스킨케어", option("타입", "무향", "시트러스", 0L), "생일", "가벼운 선물"),
            product("코튼 멀티 파우치", "라운드데이", "소지품을 단정하게 정리하는 코튼 파우치", "안쪽 분리 포켓과 탄탄한 지퍼를 더해 화장품, 충전기, 문구류를 편리하게 수납합니다.", 11_900L, "패션/가방", option("색상", "내추럴", "네이비", 0L), "생일", "가벼운 선물"),
            product("사계절 코튼 삭스 4족", "마음한칸", "차분한 색으로 구성한 데일리 양말", "부드러운 코튼 혼방 원사를 사용하고 발목을 편안하게 감싸는 기본 길이로 제작했습니다.", 15_000L, "패션/패션소품", option("색상 구성", "뉴트럴", "포인트", 0L), "생일", "가벼운 선물"),
            product("소프트 터치 무릎담요", null, "집과 사무실에서 가볍게 덮는 부드러운 담요", "가볍고 촘촘한 원단으로 보온성을 높였으며 세탁 후에도 형태가 쉽게 흐트러지지 않습니다.", 29_800L, "리빙/침구", option("색상", "오트밀", "그레이", 0L), "생일", "응원", "가벼운 선물"),
            product("웨이브 글라스 화병", "오후의정원", "한두 송이만으로도 공간을 살리는 유리 화병", "잔잔한 물결 무늬와 안정적인 바닥 설계로 식탁, 선반, 침실 어디에나 자연스럽게 어울립니다.", 17_900L, "리빙/인테리어", option("색상", "클리어", "앰버", 1_000L), "집들이", "기념일", "가벼운 선물"),
            product("무선 터치 테이블 램프", "다정상점", "세 단계 밝기를 지원하는 충전식 무드 조명", "상단을 가볍게 터치해 밝기를 조절하며 USB-C 충전 방식으로 원하는 곳에 옮겨 사용할 수 있습니다.", 38_000L, "디지털/생활가전", option("색상", "샌드 베이지", "매트 블랙", 0L), "생일", "집들이", "기념일"),
            product("내추럴 키친클로스 3장", null, "흡수력이 좋은 코튼 리넨 주방 패브릭", "서로 다른 짜임과 색상의 키친클로스 세 장을 구성해 설거지와 테이블 정리에 활용할 수 있습니다.", 20_900L, "리빙/주방용품", option("색상 구성", "웜 뉴트럴", "쿨 뉴트럴", 0L), "감사", "집들이", "가벼운 선물"),
            product("오크 우드 트레이 앤 티스푼", "포근한결", "홈카페 시간을 단정하게 완성하는 원목 구성", "단단한 오크 소재 트레이와 티스푼을 식품용 오일로 마감해 나뭇결을 자연스럽게 살렸습니다.", 32_000L, "리빙/주방용품", option("크기", "1인용", "2인용", 8_000L), "감사", "집들이", "기념일"),
            product("무광 디저트 플레이트 2P", "라운드데이", "디저트와 브런치에 어울리는 무광 접시", "담백한 색감과 부드러운 테두리로 매일 사용하기 좋으며 전자레인지와 식기세척기 사용이 가능합니다.", 34_500L, "리빙/주방용품", option("색상", "크림", "모카", 0L), "축하", "집들이", "기념일"),
            product("셀프 플라워 부케 키트", null, "직접 다듬고 포장해 완성하는 꽃다발 키트", "조화와 포장지, 리본, 메시지 태그를 함께 담아 특별한 날 나만의 꽃다발을 만들 수 있습니다.", 26_000L, "리빙/꽃·식물", option("색상", "피치", "라벤더", 0L), "생일", "축하", "기념일"),
            product("기념일 주얼리 트레이", "소담연구소", "반지와 귀걸이를 가지런히 보관하는 작은 트레이", "부드러운 곡선의 세라믹 트레이로 화장대와 협탁에 부담 없이 놓을 수 있습니다.", 19_900L, "리빙/생활용품", option("색상", "오프화이트", "로즈", 0L), "생일", "축하", "기념일"),
            product("월넛 원목 포토 프레임", "오후의정원", "소중한 사진 한 장을 담는 원목 액자", "월넛 무늬와 얇은 프레임이 사진에 자연스럽게 집중되도록 하며 세로와 가로로 모두 세울 수 있습니다.", 28_000L, "리빙/인테리어", option("크기", "4×6", "5×7", 5_000L), "생일", "축하", "기념일"),
            product("별자리 우드 오르골", null, "은은한 선율과 별자리 각인이 어우러진 오르골", "천연 원목 상자에 별자리 모티프를 새기고 태엽 방식의 잔잔한 선율을 담았습니다.", 41_000L, "리빙/인테리어", option("각인", "기본", "별자리 선택", 4_000L), "생일", "축하", "기념일"),
            product("데스크 오거나이저 컬렉션", "모먼트메이드", "책상을 효율적으로 나누는 수납 도구 구성", "펜 트레이와 메모 홀더, 소품함을 모듈처럼 조합해 업무 공간을 깔끔하게 정리합니다.", 28_900L, "리빙/생활용품", option("색상", "오프화이트", "차콜", 0L), "축하", "응원", "가벼운 선물"),
            product("저소음 컴팩트 무선 마우스", "포근한결", "휴대와 업무에 편리한 저소음 무선 마우스", "가벼운 무게와 무소음 클릭 설계, 3단계 DPI 조절을 지원해 사무실과 이동 중 사용에 적합합니다.", 24_900L, "디지털/컴퓨터 주변기기", option("색상", "오프화이트", "딥그린", 0L), "축하", "응원", "가벼운 선물"),
            product("에너지 스낵 버라이어티 박스", null, "바쁜 하루에 간편하게 즐기는 영양 간식", "에너지바와 견과, 말린 과일을 다양하게 담아 공부와 업무 중 가볍게 꺼내 먹기 좋습니다.", 31_500L, "식품/과자·간식", option("구성", "15개입", "24개입", 13_000L), "응원", "가벼운 선물"),
            product("데일리 보온보냉 텀블러 500ml", "마음한칸", "출근과 산책에 알맞은 스테인리스 텀블러", "이중 진공 구조로 온도를 오래 유지하며 넓은 입구와 분리형 패킹으로 세척이 편리합니다.", 27_000L, "리빙/주방용품", option("색상", "버터 옐로", "딥 네이비", 0L), "생일", "응원", "가벼운 선물"),
            product("실리콘 조리도구 5종", "소담연구소", "매일 쓰는 도구만 고른 실리콘 조리도구", "내열 실리콘 헤드와 단단한 손잡이를 사용해 코팅 팬에 부담이 적고 걸어서 보관할 수 있습니다.", 36_900L, "리빙/주방용품", option("색상", "오프화이트", "차콜", 0L), "축하", "집들이"),
            product("호텔 코튼 타월 4장", null, "도톰한 중량과 차분한 색감의 데일리 타월", "흡수력이 좋은 코마사 면사를 촘촘히 짜 세탁을 반복해도 부드러운 촉감이 오래갑니다.", 39_000L, "리빙/생활용품", option("색상 구성", "오프화이트", "그레이", 0L), "감사", "집들이"),
            product("시그니처 홈 디퓨저 200ml", "다정상점", "현관과 거실에 은은하게 머무는 공간 향", "부담스럽지 않은 발향과 심플한 유리 보틀로 새집과 새로운 공간에 자연스럽게 어울립니다.", 33_000L, "리빙/캔들·디퓨저", TWO_SCENTS, "감사", "집들이", "기념일"),
            product("패브릭 폴딩 수납 바스켓", "모먼트메이드", "사용하지 않을 때 접어 두는 다용도 수납함", "형태를 잡아 주는 보강재와 손잡이를 더해 의류, 장난감, 생활 소품을 편리하게 정리합니다.", 22_900L, "리빙/생활용품", CREAM_SAGE, "집들이", "가벼운 선물"),
            product("베이비 센서리 토이 박스", null, "아기의 시각과 촉각 발달을 돕는 놀이 구성", "서로 다른 질감과 소리를 경험할 수 있는 부드러운 블록과 촉감 볼을 담았습니다.", 44_000L, "유아동/완구", option("월령", "3개월 이상", "6개월 이상", 0L), "출산·육아", "축하"),
            product("오가닉 코튼 베이비 블랭킷", "라운드데이", "외출과 낮잠에 활용하는 부드러운 아기 담요", "오가닉 코튼 원단을 사용하고 가장자리를 부드럽게 마감해 민감한 피부에도 편안합니다.", 35_000L, "유아동/출산선물", option("색상", "오트밀", "스카이", 0L), "출산·육아", "축하"),
            product("베이비 마일스톤 카드 12개월", "마음한칸", "아기의 첫 1년을 사진으로 기록하는 월령 카드", "1개월부터 첫돌까지 사용할 수 있는 카드와 성장 순간을 위한 기념 카드 네 장을 함께 담았습니다.", 12_000L, "유아동/유아용품", option("디자인", "플라워", "애니멀", 0L), "출산·육아", "가벼운 선물"),
            product("맘앤대디 리프레시 스낵 박스", null, "바쁜 보호자의 짧은 휴식을 위한 간식 구성", "디카페인 티와 쿠키, 견과를 개별 포장해 수유와 육아 사이에도 간편하게 즐길 수 있습니다.", 25_900L, "식품/과자·간식", option("구성", "베이직", "라지", 10_000L), "출산·육아", "응원"),
            product("애니멀 소프트 딸랑이 3종", "오후의정원", "작은 손으로 잡기 편한 부드러운 딸랑이", "가벼운 패브릭 소재와 은은한 소리로 구성하고 세 가지 동물 캐릭터를 한 상자에 담았습니다.", 28_000L, "유아동/완구", option("구성", "숲속 동물", "바다 동물", 0L), "출산·육아", "축하"),
            product("블로섬 꽃차 10티백", "다정상점", "따뜻한 인사를 전하는 향긋한 꽃차", "국화와 메리골드 블렌드를 삼각 티백에 담아 찻잎 처리 없이 간편하게 우릴 수 있습니다.", 7_900L, "식품/커피·차", option("구성", "10티백", "20티백", 6_000L), "감사", "가벼운 선물"),
            product("응원 메시지 초콜릿 12구", null, "짧은 메시지와 함께 전하는 달콤한 초콜릿", "밀크와 다크 초콜릿을 고르게 담고 상자 안쪽에 응원 문구를 적을 수 있는 공간을 마련했습니다.", 8_500L, "식품/과자·간식", option("초콜릿", "밀크·다크", "다크 중심", 0L), "응원", "가벼운 선물"),
            product("코튼 손수건 2장 기프트팩", "포근한결", "매일 부담 없이 사용하는 부드러운 손수건", "흡수력이 좋은 면 원단에 잔잔한 패턴을 더하고 두 장을 종이 패키지에 담았습니다.", 10_900L, "패션/패션소품", option("패턴", "체크", "보태니컬", 0L), "감사", "가벼운 선물"),
            product("데스크 플랜트 세라믹 화분", "라운드데이", "책상 위에 생기를 더하는 관리 쉬운 식물", "작은 관엽식물과 배수 구멍이 있는 세라믹 화분을 함께 구성해 초보자도 관리하기 쉽습니다.", 13_900L, "리빙/꽃·식물", option("식물", "테이블야자", "스킨답서스", 0L), "응원", "집들이", "가벼운 선물"),
            product("허브 브리즈 룸 스프레이 100ml", null, "공간과 패브릭에 산뜻함을 더하는 룸 스프레이", "허브와 시트러스 계열 향을 가볍게 분사할 수 있으며 휴대하기 좋은 크기로 제작했습니다.", 16_000L, "리빙/캔들·디퓨저", option("향", "그린 허브", "시트러스 티", 0L), "감사", "집들이", "가벼운 선물"),
            product("생일 디저트 파티 박스", "소담연구소", "작은 생일 파티를 위한 다채로운 디저트", "브라우니와 쿠키, 미니 파운드를 개별 포장하고 초와 축하 태그를 함께 담았습니다.", 37_000L, "식품/베이커리·떡", option("구성", "2~3인", "4~5인", 15_000L), "생일", "축하"),
            product("프리미엄 티 셀렉션 24입", "오후의정원", "고마운 분께 전하기 좋은 고급 티 컬렉션", "홍차와 허브티 여섯 종류를 네 개씩 담아 취향에 따라 골라 마실 수 있습니다.", 52_000L, "식품/커피·차", option("구성", "24입", "36입", 18_000L), "감사", "기념일"),
            product("브런치 테이블웨어 4P", null, "두 사람의 식탁을 위한 도자기 테이블웨어", "플레이트와 볼을 두 개씩 구성해 브런치와 가벼운 저녁 식사에 활용하기 좋습니다.", 68_000L, "리빙/주방용품", option("색상", "크림", "세이지", 0L), "축하", "집들이", "기념일"),
            product("베지터블 레더 카드지갑", "모먼트메이드", "새로운 시작에 어울리는 슬림 카드지갑", "식물성 무두질 가죽을 사용하고 자주 쓰는 카드 네 장과 명함을 수납할 수 있게 설계했습니다.", 45_000L, "패션/지갑", option("색상", "탄 브라운", "딥 네이비", 0L), "생일", "축하", "기념일"),
            product("핸드드립 브루잉 스타터 컬렉션", "포근한결", "집에서 천천히 커피를 내리는 입문 도구 구성", "드리퍼와 서버, 계량 스푼, 필터를 담아 별도 도구 없이 핸드드립을 시작할 수 있습니다.", 79_000L, "리빙/주방용품", option("드리퍼", "1~2인용", "2~4인용", 8_000L), "생일", "집들이", "기념일"),
            product("딥슬립 필로우 커버 앤 아이마스크", null, "편안한 잠자리를 위한 부드러운 패브릭 구성", "피부에 닿는 면을 매끄럽게 가공한 베개 커버와 빛을 편안하게 가리는 아이마스크를 담았습니다.", 48_500L, "리빙/침구", option("색상", "샌드", "미드나잇", 0L), "생일", "응원", "가벼운 선물"),
            product("라탄 피크닉 바스켓", "마음한칸", "주말 나들이에 어울리는 클래식 피크닉 바구니", "뚜껑과 손잡이가 단단히 고정되며 안쪽 패브릭 라이너를 분리해 세탁할 수 있습니다.", 89_000L, "리빙/생활용품", SMALL_LARGE, "생일", "기념일"),
            product("셀러브레이션 프리미엄 기프트 박스", "소담연구소", "차와 캔들, 패브릭 소품을 담은 풍성한 축하 선물", "서로 다른 취향에도 부담 없이 전할 수 있는 생활 소품을 고급 하드 박스에 정갈하게 구성했습니다.", 120_000L, "리빙/생활용품", BASIC_GIFT, "감사", "축하", "기념일")
    );

    private final SellerRepository sellerRepository;
    private final CategoryRepository categoryRepository;
    private final GiftOccasionRepository giftOccasionRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRegistrationService productRegistrationService;
    private final StorageProvider storageProvider;
    private final Validator validator;

    @Transactional
    public SampleProductSeedResult seed(Long sellerId, boolean dryRun) {
        return seed(sellerId, dryRun, SampleProductImageManifest.empty());
    }

    @Transactional
    public SampleProductSeedResult seed(
            Long sellerId,
            boolean dryRun,
            SampleProductImageManifest imageManifest
    ) {
        Seller seller = getActiveSeller(sellerId);
        int existingProductCount = validateSeedState();
        if (existingProductCount == PRODUCT_COUNT) {
            return result(dryRun, 0, PRODUCT_COUNT, 0, 0, 0);
        }

        Map<String, Category> categories = resolveCategories();
        Map<String, GiftOccasion> occasions = resolveGiftOccasions();
        Map<String, SampleProductImageManifest.ImageEntry> images = validateImages(
                seller,
                imageManifest == null ? SampleProductImageManifest.empty() : imageManifest
        );

        if (dryRun) {
            return result(true, 0, 0, categories.size(), occasions.size(), images.size());
        }

        for (int index = 0; index < PRODUCT_TEMPLATES.size(); index++) {
            ProductTemplate template = PRODUCT_TEMPLATES.get(index);
            createProduct(
                    index,
                    template,
                    seller.getUser().getId(),
                    categories.get(template.categoryPath()),
                    template.giftOccasionNames().stream().map(occasions::get).toList(),
                    images.get(productCode(index))
            );
        }

        return result(false, PRODUCT_COUNT, 0, categories.size(), occasions.size(), images.size());
    }

    private Seller getActiveSeller(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SampleProductSeedException("Seller not found: " + sellerId));
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new SampleProductSeedException(
                    "Seller must be ACTIVE: sellerId=" + sellerId + ", status=" + seller.getStatus()
            );
        }
        return seller;
    }

    private Map<String, Category> resolveCategories() {
        List<Category> activeCategories = categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .toList();
        Set<Long> parentIds = activeCategories.stream()
                .map(Category::getParent)
                .filter(parent -> parent != null && parent.isActive())
                .map(Category::getId)
                .collect(java.util.stream.Collectors.toSet());

        Map<String, Category> activeLeafByPath = new LinkedHashMap<>();
        activeCategories.stream()
                .filter(category -> !parentIds.contains(category.getId()))
                .sorted(Comparator.comparing(this::categoryPath))
                .forEach(category -> activeLeafByPath.put(categoryPath(category), category));

        Set<String> requiredPaths = PRODUCT_TEMPLATES.stream()
                .map(ProductTemplate::categoryPath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> missingPaths = requiredPaths.stream()
                .filter(path -> !activeLeafByPath.containsKey(path))
                .toList();
        if (!missingPaths.isEmpty()) {
            throw new SampleProductSeedException(
                    "Required active leaf categories were not found: " + missingPaths
                            + ". Available active leaf categories: " + activeLeafByPath.keySet()
            );
        }

        return requiredPaths.stream().collect(java.util.stream.Collectors.toMap(
                path -> path,
                activeLeafByPath::get,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private String categoryPath(Category category) {
        List<String> names = new ArrayList<>();
        Category current = category;
        while (current != null) {
            names.addFirst(current.getName().trim());
            current = current.getParent();
        }
        return String.join("/", names);
    }

    private Map<String, GiftOccasion> resolveGiftOccasions() {
        Map<String, GiftOccasion> activeByName = giftOccasionRepository
                .findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .collect(java.util.stream.Collectors.toMap(
                        occasion -> occasion.getName().trim(),
                        occasion -> occasion,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> requiredNames = PRODUCT_TEMPLATES.stream()
                .flatMap(template -> template.giftOccasionNames().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> missingNames = requiredNames.stream()
                .filter(name -> !activeByName.containsKey(name))
                .toList();
        if (!missingNames.isEmpty()) {
            throw new SampleProductSeedException(
                    "Required active gift occasions were not found: " + missingNames
                            + ". Available active gift occasions: " + activeByName.keySet()
            );
        }
        return requiredNames.stream().collect(java.util.stream.Collectors.toMap(
                name -> name,
                activeByName::get,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private Map<String, SampleProductImageManifest.ImageEntry> validateImages(
            Seller seller,
            SampleProductImageManifest manifest
    ) {
        Map<String, SampleProductImageManifest.ImageEntry> images = manifest.normalizedProducts();
        if (images.isEmpty()) {
            throw new SampleProductSeedException(
                    "Image manifest is required and must contain exactly 50 products."
            );
        }

        Set<String> expectedCodes = IntStream.range(0, PRODUCT_COUNT)
                .mapToObj(this::productCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!images.keySet().equals(expectedCodes)) {
            Set<String> missing = new LinkedHashSet<>(expectedCodes);
            missing.removeAll(images.keySet());
            Set<String> unexpected = new LinkedHashSet<>(images.keySet());
            unexpected.removeAll(expectedCodes);
            throw new SampleProductSeedException(
                    "Image manifest must contain exactly 50 product codes. missing=" + missing
                            + ", unexpected=" + unexpected
            );
        }

        String representativePrefix = "products/" + seller.getId() + "/representative/";
        String galleryPrefix = "products/" + seller.getId() + "/gallery/";
        Set<String> uniqueKeys = new HashSet<>();

        for (Map.Entry<String, SampleProductImageManifest.ImageEntry> manifestEntry : images.entrySet()) {
            SampleProductImageManifest.ImageEntry image = manifestEntry.getValue();
            if (image == null) {
                throw new SampleProductSeedException("Image manifest entry is empty: " + manifestEntry.getKey());
            }
            validateObjectKey(
                    manifestEntry.getKey(),
                    image.representativeImageKey(),
                    representativePrefix,
                    uniqueKeys
            );
            List<String> galleryKeys = image.normalizedGalleryImageKeys();
            if (galleryKeys.size() > 10) {
                throw new SampleProductSeedException(
                        "Gallery images cannot exceed 10: " + manifestEntry.getKey()
                );
            }
            for (String galleryKey : galleryKeys) {
                validateObjectKey(manifestEntry.getKey(), galleryKey, galleryPrefix, uniqueKeys);
            }
        }
        return images;
    }

    private void validateObjectKey(
            String productCode,
            String objectKey,
            String requiredPrefix,
            Set<String> uniqueKeys
    ) {
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(requiredPrefix)) {
            throw new SampleProductSeedException(
                    "Invalid image key for " + productCode + ". Required prefix: " + requiredPrefix
            );
        }
        if (!uniqueKeys.add(objectKey)) {
            throw new SampleProductSeedException("Image key is duplicated: " + objectKey);
        }
        if (!storageProvider.objectExists(objectKey)) {
            throw new SampleProductSeedException("Image object does not exist: " + objectKey);
        }
    }

    private int validateSeedState() {
        List<ProductVariant> existingVariants = variantRepository
                .findAllBySkuCodeStartingWithOrderBySkuCodeAsc(SKU_PREFIX);
        if (existingVariants.isEmpty()) {
            return 0;
        }

        Set<String> actualSkuCodes = existingVariants.stream()
                .map(ProductVariant::getSkuCode)
                .collect(java.util.stream.Collectors.toSet());
        long distinctProducts = existingVariants.stream()
                .map(variant -> variant.getProduct().getId())
                .distinct()
                .count();
        if (actualSkuCodes.equals(expectedSkuCodes()) && distinctProducts == PRODUCT_COUNT) {
            return PRODUCT_COUNT;
        }
        throw new SampleProductSeedException(
                "Seed marker conflict or partial seed detected. No data was changed. seedId="
                        + SEED_ID + ", variants=" + existingVariants.size() + ", products=" + distinctProducts
        );
    }

    private void createProduct(
            int index,
            ProductTemplate template,
            Long sellerUserId,
            Category category,
            List<GiftOccasion> occasions,
            SampleProductImageManifest.ImageEntry images
    ) {
        boolean freeShipping = index % 3 != 1;
        int firstStock = 24 + index % 12;
        int secondStock = 16 + index % 9;

        ProductCreateRequest product = new ProductCreateRequest(
                category.getId(),
                template.name(),
                template.brandName(),
                template.summary(),
                description(template),
                template.price(),
                firstStock + secondStock,
                images.representativeImageKey(),
                images.normalizedGalleryImageKeys(),
                freeShipping,
                freeShipping ? 0L : 3_000L + (index % 2) * 500L,
                2 + index % 3,
                3_000L,
                6_000L,
                false
        );
        ProductOptionUpdateRequest options = new ProductOptionUpdateRequest(List.of(
                new ProductOptionGroupRequest(
                        null,
                        template.option().name(),
                        0,
                        List.of(
                                new ProductOptionValueRequest(null, template.option().firstValue(), 0),
                                new ProductOptionValueRequest(null, template.option().secondValue(), 1)
                        )
                )
        ));
        List<ProductRegistrationVariantRequest> variants = List.of(
                registrationVariant(index, "A", 0, 0L, firstStock),
                registrationVariant(
                        index,
                        "B",
                        1,
                        template.option().secondAdditionalPrice(),
                        secondStock
                )
        );

        ProductRegistrationRequest request = new ProductRegistrationRequest(
                product,
                options,
                variants,
                occasions.stream().map(GiftOccasion::getId).toList(),
                null
        );
        validateRegistrationRequest(index, request);
        productRegistrationService.registerProduct(sellerUserId, request);
    }

    private void validateRegistrationRequest(int index, ProductRegistrationRequest request) {
        Set<ConstraintViolation<ProductRegistrationRequest>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        List<String> reasons = violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .toList();
        throw new SampleProductSeedException(
                "Product registration validation failed: code=" + productCode(index) + ", reasons=" + reasons
        );
    }

    private ProductRegistrationVariantRequest registrationVariant(
            int index,
            String suffix,
            int optionValueSortOrder,
            long additionalPrice,
            int stockQuantity
    ) {
        return new ProductRegistrationVariantRequest(
                sku(index, suffix),
                List.of(new ProductOptionReferenceRequest(0, optionValueSortOrder)),
                additionalPrice,
                stockQuantity,
                true
        );
    }

    private String description(ProductTemplate template) {
        return "<p>" + template.summary() + "</p><p>" + template.detail() + "</p>";
    }

    private Set<String> expectedSkuCodes() {
        return IntStream.range(0, PRODUCT_COUNT)
                .boxed()
                .flatMap(index -> java.util.stream.Stream.of(sku(index, "A"), sku(index, "B")))
                .collect(java.util.stream.Collectors.toSet());
    }

    private String productCode(int zeroBasedIndex) {
        return SKU_PREFIX + "%03d".formatted(zeroBasedIndex + 1);
    }

    private String sku(int zeroBasedIndex, String suffix) {
        return productCode(zeroBasedIndex) + "-" + suffix;
    }

    private SampleProductSeedResult result(
            boolean dryRun,
            int created,
            int skipped,
            int categoryCount,
            int giftOccasionCount,
            int imagesAttached
    ) {
        return new SampleProductSeedResult(
                SEED_ID,
                dryRun,
                PRODUCT_COUNT,
                created,
                skipped,
                categoryCount,
                giftOccasionCount,
                imagesAttached
        );
    }

    private static ProductTemplate product(
            String name,
            String brandName,
            String summary,
            String detail,
            long price,
            String categoryPath,
            OptionTemplate option,
            String... giftOccasionNames
    ) {
        return new ProductTemplate(
                name,
                brandName,
                summary,
                detail,
                price,
                categoryPath,
                option,
                List.of(giftOccasionNames)
        );
    }

    private static OptionTemplate option(
            String name,
            String firstValue,
            String secondValue,
            long secondAdditionalPrice
    ) {
        return new OptionTemplate(name, firstValue, secondValue, secondAdditionalPrice);
    }

    private record ProductTemplate(
            String name,
            String brandName,
            String summary,
            String detail,
            long price,
            String categoryPath,
            OptionTemplate option,
            List<String> giftOccasionNames
    ) {
    }

    private record OptionTemplate(
            String name,
            String firstValue,
            String secondValue,
            long secondAdditionalPrice
    ) {
    }
}
