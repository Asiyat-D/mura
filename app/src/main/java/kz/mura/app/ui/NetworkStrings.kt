package kz.mura.app.ui

fun Strings.n(ru: String): String {
    if(language == "ru") return ru
    return networkStrings[ru]?.get(if(language == "kk") 0 else 1) ?: ru
}
fun Strings.delivery(value: String): String = n(when(value) { "queued"->"В очереди"; "sent"->"Отправлено"; "delivered"->"Доставлено"; "received"->"Получено"; "expired"->"Срок доставки истёк"; else->"Локально" })
fun Strings.networkError(value: String): String = n(when(value) {
    "peer_not_ready"->"Собеседник ещё не добавил ваш код контакта."
    "recipient_missing"->"Собеседник ещё не подключился к серверу."
    "queue_full"->"Очередь собеседника заполнена. Повторим позже."
    "own_contact"->"Это ваш код. Добавьте код собеседника."
    "different_server"->"Укажите один сервер на обоих устройствах и обменяйтесь актуальными кодами."
    "offline"->"Нет связи с сервером. Сообщения остаются в очереди."
    "invalid_server"->"Укажите HTTPS-адрес сервера без пути, параметров или пароля."
    else->"Не удалось выполнить действие. Проверьте код контакта целиком и адрес сервера."
})
private val networkStrings=mapOf(
    "Подключение…" to listOf("Қосылуда…","Connecting…"),
    "Подключено · сквозное шифрование" to listOf("Қосылды · ұшынан ұшына шифрлау","Connected · end-to-end encrypted"),
    "Нет связи" to listOf("Байланыс жоқ","Disconnected"),
    "Мой код" to listOf("Менің кодым","My code"),
    "Мой код контакта" to listOf("Менің контакт кодым","My contact code"),
    "Код контакта" to listOf("Контакт коды","Contact code"),
    "Добавить контакт" to listOf("Контакт қосу","Add contact"),
    "Каждый добавляет код другого. Сообщения приходят, пока Mura открыта." to listOf("Әрқайсысы екіншісінің кодын қосады. Mura ашық кезде хабарламалар келеді.","Each person adds the other's code. Messages arrive while Mura is open."),
    "Отправьте свой код собеседнику и добавьте его код у себя." to listOf("Кодыңызды сұхбаттасушыға жіберіп, оның кодын қосыңыз.","Share your code with your contact and add their code."),
    "Мой код безопасности" to listOf("Менің қауіпсіздік кодым","My security code"),
    "Код безопасности" to listOf("Қауіпсіздік коды","Security code"),
    "Сверьте этот код с кодом владельца контакта на его телефоне." to listOf("Кодты контакт иесінің телефонындағы кодпен салыстырыңыз.","Compare this code with the contact owner's code on their phone."),
    "Сообщения приходят, пока Mura открыта и разблокирована. Уведомлений пока нет." to listOf("Mura ашық және құлыптан босатылғанда хабарламалар келеді. Хабарландырулар әзірге жоқ.","Messages arrive while Mura is open and unlocked. Notifications are not available yet."),
    "Переписка через интернет" to listOf("Интернет арқылы хат алмасу","Internet messaging"),
    "Адрес сервера" to listOf("Сервер мекенжайы","Server address"),
    "Подключить сервер" to listOf("Серверге қосылу","Connect server"),
    "На обоих устройствах укажите один сервер. Mac должен быть включён, без сна и с интернетом." to listOf("Екі құрылғыда бір серверді көрсетіңіз. Mac қосулы, ұйқысыз және интернетпен болуы керек.","Use the same server on both devices. The Mac must stay on, awake and online."),
    "В очереди" to listOf("Кезекте","Queued"), "Отправлено" to listOf("Жіберілді","Sent"), "Доставлено" to listOf("Жеткізілді","Delivered"), "Получено" to listOf("Алынды","Received"),
    "Срок доставки истёк" to listOf("Жеткізу мерзімі өтті","Delivery expired"), "Локально" to listOf("Жергілікті","Local only"), "Локальная запись" to listOf("Жергілікті жазба","Local note"),
    "Отправить" to listOf("Жіберу","Send"), "Сохранить локально" to listOf("Жергілікті сақтау","Save locally"),
    "Переписка и контакт будут удалены с этого телефона. Для переписки добавьте контакт снова." to listOf("Хаттар мен контакт осы телефоннан жойылады. Хат алмасу үшін контактіні қайта қосыңыз.","This phone's conversation and contact will be deleted. Add the contact again to resume messaging."),
    "Собеседник ещё не добавил ваш код контакта." to listOf("Сұхбаттасушы сіздің кодыңызды әлі қоспаған.","Your contact hasn't added your code yet."),
    "Собеседник ещё не подключился к серверу." to listOf("Сұхбаттасушы серверге әлі қосылмаған.","Your contact hasn't connected to the server yet."),
    "Очередь собеседника заполнена. Повторим позже." to listOf("Сұхбаттасушының кезегі толы. Кейін қайталаймыз.","Your contact's queue is full. We'll retry later."),
    "Это ваш код. Добавьте код собеседника." to listOf("Бұл сіздің кодыңыз. Сұхбаттасушының кодын қосыңыз.","This is your own code. Add your contact's code."),
    "Укажите один сервер на обоих устройствах и обменяйтесь актуальными кодами." to listOf("Екі құрылғыда бір серверді көрсетіп, жаңа кодтармен алмасыңыз.","Set the same server on both devices and exchange current codes."),
    "Нет связи с сервером. Сообщения остаются в очереди." to listOf("Сервермен байланыс жоқ. Хабарламалар кезекте қалады.","Cannot reach the server. Messages remain queued."),
    "Укажите HTTPS-адрес сервера без пути, параметров или пароля." to listOf("Жолсыз, параметрсіз және құпиясөзсіз HTTPS сервер мекенжайын көрсетіңіз.","Enter an HTTPS server address without a path, parameters or password."),
    "Не удалось выполнить действие. Проверьте код контакта целиком и адрес сервера." to listOf("Әрекет орындалмады. Толық контакт кодын және сервер мекенжайын тексеріңіз.","Couldn't complete the action. Check the full contact code and server address.")
)
