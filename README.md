# TP1 - Currency Converter

Desarrollo de Software Profesional (ITBA, 2026) - Trabajo Practico 1.

Este proyecto toma el `CurrencyConverter` revisado en la Clase 1 (paquete
`edu.itba.class1.exchange`: `CurrencyConverter`, `ExchangeRateProvider`,
`FreeCurrencyApiExchangeRateProvider`, `MoneyAmount`) y le agrega las 7
funcionalidades pedidas en la consigna, usando la API de
[freecurrencyapi.com](https://freecurrencyapi.com/).

## Diseno

La regla de negocio (`CurrencyConverter`) depende unicamente de la abstraccion
`ExchangeRateProvider`. No sabe si las cotizaciones vienen de una API HTTP, un
archivo o una base de datos. El unico detalle que sabe de HTTP/JSON es
`FreeCurrencyApiExchangeRateProvider` (usa Unirest + Gson, igual que en clase),
que implementa esa interfaz. Esto es el mismo ejemplo de inversion de
dependencias visto en la Clase 1 (`CurrencyConverter -> ExchangeRateProvider <-
FreeCurrencyApiExchangeRateProvider`), aplicado ahora a un contrato mas rico
(listar monedas, cotizacion simple, cotizacion en lote e historica) en lugar
del `getExchangeRate(from, to)` de una sola moneda que se vio en clase.

```
CurrencyConverter  --(depende de)-->  ExchangeRateProvider (interfaz)
                                              ^
                                              |
                                FreeCurrencyApiExchangeRateProvider (detalle: Unirest + Gson)
```

Los montos se modelan con `MoneyAmount` (wrapper inmutable de `BigDecimal`,
igual que en clase) y las monedas con `java.util.Currency` (no se reinventa un
tipo propio: la clase estandar ya da codigo, simbolo y nombre).

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

El metodo original `convert(Currency from, Currency to, MoneyAmount amount)`
visto en clase se mantiene (delega en el nuevo `convert(from, amount,
List.of(to))`), pero **ya no traga errores**: la version de clase hacia
`catch (Exception e) { System.err.println(...); } return MoneyAmount.ZERO;`,
que es exactamente el "simplemente fallar" que la consigna (historia 4) pide
evitar. Ahora la excepcion se propaga tal cual, tipada, para que el que llama
pueda reaccionar en vez de recibir un 0 silencioso.

## Manejo de errores (historia 4)

- `CurrencyExchangeConnectionException`: no se pudo hablar con la API (sin
  respuesta HTTP: timeout, DNS, conexion rechazada, etc).
- `CurrencyExchangeApiException`: la API respondio pero con error (401, 403,
  404, 429, 500...). Expone `getStatusCode()` y, cuando la API lo informa,
  `getErrorCode()` (p.ej. `invalid_api_key`).
- `UnknownCurrencyException`: se pidio una moneda cuya cotizacion no vino en
  la respuesta (p.ej. codigo mal escrito).

Las tres heredan de `CurrencyExchangeException` (unchecked), asi que
`CurrencyConverter` no necesita capturarlas: se propagan hasta quien use la
libreria, que decide como notificarlas (`Main` las captura una sola vez, en el
borde de la aplicacion, para imprimir un mensaje claro).

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
mvn -q compile exec:java "-Dexec.mainClass=edu.itba.class1.exchange.Main" "-Dexec.args=--amount=250.50 --from=ARS --to=USD,EUR --date=2025-08-20"
```

- `--amount`: monto a convertir.
- `--from`: moneda de origen en formato ISO 4217.
- `--to`: una o mas monedas de destino separadas por comas.
- `--date`: fecha historica en formato `YYYY-MM-DD`.

Todos los parametros son opcionales e independientes. Los valores por defecto
son `--amount=100`, `--from=USD`, `--to=EUR,JPY` y `--date=2024-11-20`.

## Tests y cobertura

- `CurrencyConverterTest`: testea las reglas de negocio con un
  `ExchangeRateProvider` mockeado (Mockito) - sin red, sin HTTP.
- `MoneyAmountTest`: cubre `MoneyAmount` (igualdad, `multiply`, validaciones).

Surefire ejecuta los unit tests. `FreeCurrencyApiExchangeRateProvider` no forma
parte de la suite porque es la frontera con la API externa. `Main` tampoco se
testea porque es el composition-root/demo de la aplicacion.

**Nota:** se verificaron 20 unit tests con 100% de lineas y ramas dentro del
alcance unitario definido.

## Requisitos no funcionales

1. Codigo limpio: metodos chicos y de una sola responsabilidad, nombres
   explicitos, sin duplicacion entre `convert(...)` y sus variantes (todas
   delegan en `buildConversionResult` / `rateFor`).
2. Dependencias: el negocio (`CurrencyConverter`) depende de una abstraccion
   (`ExchangeRateProvider`); el detalle (HTTP/JSON/Unirest/Gson) esta aislado
   en `FreeCurrencyApiExchangeRateProvider`.
3. Sin arquitectura especifica forzada, solo esa separacion negocio/detalle.
4. Cobertura de unit tests 100% (ver seccion anterior).
