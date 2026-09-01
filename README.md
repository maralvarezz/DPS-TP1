# TP1 - Currency Converter

Desarrollo de Software Profesional (ITBA, 2026) - Trabajo Practico 1.

Este proyecto toma el `CurrencyConverter` revisado en la Clase 1 (paquete
`edu.itba.class1.exchange`: `CurrencyConverter`, `ExchangeRateProvider`,
`FreeCurrencyApiExchangeRateProvider`, `MoneyAmount`) y le agrega las 7
funcionalidades pedidas en la consigna, usando la API de
[freecurrencyapi.com](https://freecurrencyapi.com/).

## Como correrlo

Requiere Java 21+. `Main` ya trae configurada una API key de
freecurrencyapi.com (igual que la version de clase), asi que no hace falta
setear nada para probarlo.

```bash
mvn clean verify
mvn -q compile exec:java "-Dexec.mainClass=edu.itba.class1.exchange.Main"
```

`mvn clean verify` compila y corre todos los tests.

La conversion tambien se puede configurar por parametros:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=edu.itba.class1.exchange.Main" "-Dexec.args=--amount=250.50 --from=USD --to=JPY,EUR --date=2025-08-20"
```

- `--amount`: monto a convertir.
- `--from`: moneda de origen en formato ISO 4217.
- `--to`: una o mas monedas de destino separadas por comas.
- `--date`: fecha historica en formato `YYYY-MM-DD`.

Todos los parametros son opcionales e independientes. Los valores por defecto
son `--amount=100`, `--from=USD`, `--to=EUR,JPY` y `--date=2024-11-20`.

## Las 7 funcionalidades

| # | Historia de usuario | Donde vive |
|---|---|---|
| 1 | Listar monedas soportadas | `CurrencyConverter.listSupportedCurrencies()` |
| 2 | Timestamp de cuando se obtuvo la cotizacion | `ExchangeRate.fetchedAt()` / `ConversionResult.fetchedAt()` |
| 3 | Cotizacion entre dos monedas sin convertir un monto | `CurrencyConverter.getExchangeRate(from, to)` -> `ExchangeRate` |
| 4 | Manejo y notificacion clara de errores de conexion/API | Jerarquia `CurrencyExchangeException` (ver abajo) |
| 5 | Convertir un monto a varias monedas a la vez | `CurrencyConverter.convert(from, amount, List<Currency> to)` -> `ConversionResult` |
| 6 | Cotizacion historica para una fecha pasada | `CurrencyConverter.convert(from, amount, List<Currency> to, LocalDate date)` |
| 7 | Ver la cotizacion usada por cada moneda | `ConversionResult.conversions()` -> `Map<Currency, ConversionDetail>`, cada `ConversionDetail` trae `rate()` y `convertedAmount()` |


## Manejo de errores

- `CurrencyExchangeConnectionException`: no se pudo hablar con la API (sin
  respuesta HTTP: timeout, DNS, conexion rechazada, etc).
- `CurrencyExchangeApiException`: la API respondio pero con error (401, 403,
  404, 429, 500...). 
- `UnknownCurrencyException`: se pidio una moneda cuya cotizacion no vino en
  la respuesta (p.ej. codigo mal escrito).

Las tres heredan de `CurrencyExchangeException` (unchecked), asi que
`CurrencyConverter` no necesita capturarlas: se propagan hasta quien use la
libreria, que decide como notificarlas (`Main` las captura una sola vez, en el
borde de la aplicacion, para imprimir un mensaje claro).


## Tests y cobertura

- `CurrencyConverterTest`: testea las reglas de negocio con un
  `ExchangeRateProvider` mockeado (Mockito) - sin red, sin HTTP.
- `MoneyAmountTest`: cubre `MoneyAmount` (igualdad, `multiply`, validaciones).

Surefire ejecuta los unit tests. `FreeCurrencyApiExchangeRateProvider` no forma
parte de la suite porque es la frontera con la API externa. `Main` tampoco se
testea porque es el composition-root/demo de la aplicacion.

**Nota:** se verificaron 20 unit tests con 100% de lineas y ramas dentro del
alcance unitario definido.

