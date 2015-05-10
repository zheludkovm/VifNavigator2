Vif Навигатор

Установка приложения 

1. Копируем apk из папки build в телефон
2. Разрешаем установку внешних приложений
3. устанавливаем

Сборка

1. Ставим JDK

    http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Ставим Leiningen

    http://leiningen.org/
3. Ставим Android sdk

    https://developer.android.com/sdk/index.html#Other
4. Прописываем путь к android sdk в vif-navigator/project.clj в :sdk-path
5. Собираем командой

    lein modules do build
    
Установка приложения после сборки вручную

1. Разрешаем на телефоне установку сторонних приложений

2. Устанавливаем на телефоне vif-navigator.apk из vif-navigator/target/debug

