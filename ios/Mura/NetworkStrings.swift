import Foundation

extension Strings {
    func n(_ russian: String) -> String {
        guard language != "ru", let values = networkTranslations[russian] else { return russian }
        return values[language == "kk" ? 0 : 1]
    }
}
private let networkTranslations: [String: [String]] = [
    "Онлайн": ["Онлайн", "Online"],
    "Нет связи": ["Байланыс жоқ", "Disconnected"],
    "Подключение": ["Қосылу", "Connection"],
    "Подключение…": ["Қосылуда…", "Connecting…"],
    "Подключено · сквозное шифрование": ["Қосылды · ұшынан ұшына шифрлау", "Connected · end-to-end encrypted"],
    "Укажите сервер в настройках": ["Баптауларда серверді көрсетіңіз", "Set a server in Settings"],
    "Мой код": ["Менің кодым", "My code"],
    "Обменяйтесь кодами контактов: каждый добавляет код другого. Сообщения приходят, пока Mura открыта.": ["Контакт кодтарын алмасыңыз: әрқайсысы екіншісінің кодын қосады. Mura ашық кезде хабарламалар келеді.", "Exchange contact codes: each person adds the other's code. Messages arrive while Mura is open."],
    "Локальная запись · добавьте собеседника по коду для переписки": ["Жергілікті жазба · хат алмасу үшін контакт кодын қосыңыз", "Local note · add a contact code to chat online"],
    "Каждый из вас добавляет код другого. В коде находятся открытые ключи и адрес сервера.": ["Әрқайсыңыз екіншісінің кодын қосасыз. Кодта ашық кілттер мен сервер мекенжайы бар.", "Each person adds the other's code. The code contains public keys and the server address."],
    "Собеседник": ["Сұхбаттасушы", "Contact"],
    "Добавить контакт": ["Контакт қосу", "Add contact"],
    "Мой код контакта": ["Менің контакт кодым", "My contact code"],
    "Отправьте этот код собеседнику и добавьте его код у себя. Код не содержит пароля или закрытых ключей.": ["Бұл кодты сұхбаттасушыға жіберіп, оның кодын қосыңыз. Кодта құпиясөз не жабық кілт жоқ.", "Share this code and add the other person's code. It contains no passwords or private keys."],
    "Мой код безопасности": ["Менің қауіпсіздік кодым", "My security code"],
    "Сообщения ожидают получателя на сервере до 7 дней. Mura получает их при открытом приложении.": ["Хабарламалар серверде 7 күнге дейін күтіледі. Mura оларды ашылған кезде алады.", "Messages wait on the server for up to 7 days. Mura receives them while the app is open."],
    "Контакт": ["Контакт", "Contact"],
    "Подключить сервер": ["Серверге қосылу", "Connect server"],
    "ПЕРЕПИСКА ЧЕРЕЗ ИНТЕРНЕТ": ["ИНТЕРНЕТ АРҚЫЛЫ ХАТ АЛМАСУ", "INTERNET MESSAGING"],
    "На обоих iPhone должен быть указан один сервер. Для текущего сервера этот Mac должен оставаться включённым и подключённым к интернету.": ["Екі iPhone бір серверді қолдануы керек. Қазіргі сервер үшін бұл Mac қосулы және интернетке қосылған болуы керек.", "Both iPhones must use the same server. For this server, keep this Mac on and connected to the internet."],
    "Код безопасности": ["Қауіпсіздік коды", "Security code"],
    "Сверьте этот код с кодом владельца контакта на его телефоне.": ["Бұл кодты контакт иесінің телефонындағы кодпен салыстырыңыз.", "Compare this code with the contact owner's code on their phone."],
    "Локальная запись": ["Жергілікті жазба", "Local note"],
    "Готово": ["Дайын", "Done"],
    "Отправить": ["Жіберу", "Send"],
    "В очереди": ["Кезекте", "Queued"],
    "Отправлено": ["Жіберілді", "Sent"],
    "Доставлено": ["Жеткізілді", "Delivered"],
    "Получено": ["Алынды", "Received"],
    "Срок доставки истёк": ["Жеткізу мерзімі өтті", "Delivery expired"],
    "Локально": ["Жергілікті", "Local only"],
    "Неверный код контакта. Скопируйте его целиком.": ["Контакт коды қате. Оны толығымен көшіріңіз.", "Invalid contact code. Copy the entire code."],
    "Укажите адрес сервера HTTPS без пути и параметров.": ["Жолсыз және параметрсіз HTTPS сервер мекенжайын көрсетіңіз.", "Enter an HTTPS server address without a path or query."],
    "Контакт использует другой сервер. Укажите тот же сервер в настройках.": ["Контакт басқа серверді қолданады. Баптауларда сол серверді көрсетіңіз.", "This contact uses a different server. Select the same server in Settings."],
    "Это ваш собственный код. Добавьте код собеседника.": ["Бұл өз кодыңыз. Сұхбаттасушының кодын қосыңыз.", "This is your own code. Add the other person's code."],
    "Не удалось проверить защищённое сообщение.": ["Қорғалған хабарламаны тексеру мүмкін болмады.", "The encrypted message could not be authenticated."],
    "Нет связи с сервером. Сообщения остаются в очереди.": ["Сервермен байланыс жоқ. Хабарламалар кезекте қалады.", "Cannot reach the server. Messages remain queued."],
    "Собеседник ещё не добавил ваш код контакта.": ["Сұхбаттасушы контакт кодыңызды әлі қоспады.", "The other person has not added your contact code yet."],
    "Собеседник ещё не подключился к серверу.": ["Сұхбаттасушы серверге әлі қосылмады.", "The other person has not connected to the server yet."],
    "Очередь собеседника заполнена. Повторим отправку позже.": ["Сұхбаттасушы кезегі толы. Кейін қайта жібереміз.", "The recipient's queue is full. We will retry later."],
    "Слишком много запросов. Повторим подключение позже.": ["Сұрау тым көп. Кейін қайта қосыламыз.", "Too many requests. We will retry later."],
    "Сервер временно недоступен. Сообщения остаются в очереди.": ["Сервер уақытша қолжетімсіз. Хабарламалар кезекте қалады.", "The server is temporarily unavailable. Messages remain queued."]
]
