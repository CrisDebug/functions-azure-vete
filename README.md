# Azure Functions - Usuarios y Roles | Cloud Native II

## 1. Descripción

Este proyecto contiene las **Azure Functions responsables de las operaciones de Usuarios y Roles**, utilizando **Java, Spring Boot, Azure Functions y Oracle Database**.

Las Functions forman parte de una arquitectura Cloud Native donde las responsabilidades se distribuyen por **dominio funcional**, evitando concentrar toda la lógica en una única función monolítica.

La solución se complementa con un **BFF desarrollado en Spring Boot**, encargado de exponer una API orientada al cliente y delegar las operaciones hacia las Functions.

```text
Cliente
   |
   v
BFF - Spring Boot
   |
   +--------------------+
   |                    |
   v                    v
Usuarios Function    Roles Function
   |                    |
   +----------+---------+
              |
              v
        Oracle Database
```

---

# 2. Dominios funcionales

## Usuarios

Las operaciones relacionadas con usuarios están agrupadas bajo el dominio:

```text
usuariosJava
```

Responsabilidades:

* Crear usuarios.
* Consultar usuarios.
* Actualizar usuarios.
* Eliminar usuarios.
* Asociar usuarios con un rol mediante `rolId`.

## Roles

Las operaciones relacionadas con roles están agrupadas bajo:

```text
rolesJava
```

Responsabilidades:

* Crear roles.
* Consultar roles.
* Actualizar roles.
* Eliminar roles.

### ¿Por qué separar Usuarios y Roles?

La separación responde a un criterio de **dominio funcional y cohesión**.

En lugar de crear una Azure Function diferente para cada operación:

```text
crearUsuario
listarUsuario
actualizarUsuario
eliminarUsuario
crearRol
listarRol
...
```

se agrupan las operaciones relacionadas:

```text
usuariosJava
rolesJava
```

Esto permite:

* Reducir la cantidad de Functions.
* Mantener responsabilidades claras.
* Facilitar el mantenimiento.
* Simplificar el despliegue.
* Mantener cada contexto funcional acotado.
* Facilitar futuras modificaciones sin afectar otros dominios.

La decisión busca equilibrar **granularidad serverless y simplicidad operacional**.

---

# 3. Arquitectura de integración

La arquitectura utiliza el patrón **Backend for Frontend (BFF)**.

El cliente no necesita conocer directamente las URLs de las Azure Functions.

```text
                 Cliente
                    |
                    v
             +-------------+
             |     BFF     |
             | Spring Boot |
             +------+------+
                    |
          +---------+---------+
          |                   |
          v                   v
   usuariosJava          rolesJava
          |                   |
          +---------+---------+
                    |
                    v
              Oracle Database
```

El BFF funciona como una capa de:

* Adaptación.
* Integración.
* Composición.
* Abstracción de los servicios internos.

Esto permitió implementar posteriormente un endpoint compuesto:

```text
GET /bff/usuarios-con-roles
```

que entrega información de usuarios y roles en una única respuesta.

---

# 4. Persistencia en Oracle

Las Azure Functions son responsables de ejecutar las operaciones de persistencia contra Oracle.

Se implementó una configuración centralizada mediante:

```text
OracleConnection.java
```

La conexión utiliza el **Oracle Wallet**, evitando almacenar directamente los certificados necesarios para establecer la conexión segura.

El wallet se mantiene fuera del control de versiones mediante `.gitignore`.

```text
src/main/resources/wallet/
```

No se deben versionar:

```text
*.pem
*.p12
*.sso
*.jks
```

---

# 5. Azure Functions y modelo Serverless

Las Functions permiten ejecutar la lógica bajo un modelo serverless, donde la infraestructura necesaria para ejecutar el código es administrada por Azure.

La arquitectura permite desacoplar:

```text
Cliente
   ↓
BFF
   ↓
Azure Functions
   ↓
Oracle
```

Cada dominio puede evolucionar independientemente.

---

# 6. Flex Consumption

Uno de los objetivos del proyecto fue trabajar con un modelo de ejecución serverless moderno.

**Flex Consumption** permite ejecutar Azure Functions con características orientadas a cargas variables y escalamiento automático.

Entre sus principales ventajas se consideran:

* Escalamiento automático.
* Pago asociado al consumo.
* Menor necesidad de administrar infraestructura.
* Capacidad de manejar variaciones en la demanda.
* Integración con arquitecturas event-driven y HTTP.
* Posibilidad de configurar recursos de ejecución de manera más flexible.

La elección de un modelo serverless permite concentrar el esfuerzo en la lógica de negocio y reducir la responsabilidad sobre servidores tradicionales.

---

# 7. Decisiones técnicas importantes

## Agrupación por dominio

Se decidió agrupar operaciones relacionadas en Functions por dominio:

```text
Usuarios
Roles
```

en lugar de crear una Function independiente por cada endpoint.

La decisión busca mantener un equilibrio entre:

```text
Desacoplamiento
      +
Cohesión
      +
Simplicidad operacional
```

---

## BFF como capa de composición

El BFF permite evitar que el cliente tenga que realizar múltiples llamadas directamente a las Functions.

Por ejemplo:

```text
GET /bff/usuarios-con-roles
```

puede obtener:

```text
Usuarios
+
Roles
```

y entregar una respuesta consolidada.

Esto permite que la arquitectura interna pueda evolucionar sin modificar necesariamente al consumidor.

---

# 8. Hitos técnicos del proyecto

## Hito 1 - Implementación inicial

Se implementaron las Functions correspondientes a:

```text
usuariosJava
rolesJava
```

con operaciones HTTP para trabajar con Oracle.

---

## Hito 2 - Integración con Oracle

Se configuró la conexión utilizando Oracle Wallet.

El principal desafío fue mantener la conectividad segura entre el entorno serverless y Oracle.

La solución incorporó una clase de conexión centralizada:

```text
OracleConnection.java
```

Esto permitió reutilizar la configuración de conexión desde las diferentes Functions.

---

## Hito 3 - Adaptación del BFF

El BFF fue configurado para utilizar las URLs de las Functions mediante propiedades externas.

De esta manera no se dejó la URL directamente codificada dentro de los controladores.

```text
application.properties
application-ec2.properties
```

Esto permite utilizar diferentes configuraciones según el ambiente.

---

## Hito 4 - Problema con DELETE

Durante las pruebas se detectó una diferencia importante entre la ruta utilizada por el BFF y el contrato real de la Azure Function.

La Function esperaba:

```http
DELETE /api/rolesJava?id=22
```

y no:

```http
DELETE /api/rolesJava/22
```

La solución se realizó en el BFF, transformando:

```http
DELETE /bff/roles/22
```

en:

```http
DELETE /api/rolesJava?id=22
```

Esto permitió mantener una API limpia hacia el cliente sin modificar la Function.

---

## Hito 5 - Endpoint compuesto

Se implementó:

```http
GET /bff/usuarios-con-roles
```

Este endpoint demuestra una de las principales capacidades del patrón BFF:

```text
Usuario
   +
Rol
   ↓
Respuesta consolidada
```

Ejemplo conceptual:

```json
{
  "usuarios": [
    {
      "id": 1,
      "nombre": "Juan Perez",
      "rolId": 1,
      "rol": "Administrador Principal"
    }
  ],
  "roles": [
    {
      "id": 1,
      "nombre": "Administrador Principal"
    }
  ]
}
```

---

# 9. Problemas técnicos y resolución

Durante la implementación se presentaron diferentes dificultades relacionadas principalmente con:

* Conectividad con Oracle.
* Oracle Wallet.
* Configuración de Azure Functions.
* Diferencias entre contratos HTTP.
* Configuración de ambientes.
* Construcción de imágenes Docker.
* Comunicación entre EC2, BFF y Azure.
* Despliegue y actualización del contenedor.

La estrategia utilizada fue resolver los problemas **por capas**, evitando modificar componentes que ya funcionaban.

```text
Problema
   ↓
Identificar capa afectada
   ↓
Validar contrato
   ↓
Probar directamente el componente
   ↓
Modificar únicamente la capa necesaria
   ↓
Volver a probar mediante el BFF
```

Un ejemplo concreto fue el problema del `DELETE`: primero se validó directamente la Function mediante `curl` y se confirmó que:

```http
DELETE /api/rolesJava?id=22
```

funcionaba correctamente.

Por lo tanto, no fue necesario modificar la Function y la adaptación se realizó exclusivamente en el BFF.

---

# 10. Estrategia de troubleshooting

Cuando una operación falla, se recomienda validar en este orden:

```text
1. Cliente
      ↓
2. BFF
      ↓
3. Azure Function
      ↓
4. Oracle
```

### Paso 1 - Validar el BFF

```bash
docker logs --tail 50 ms-bff
```

### Paso 2 - Validar directamente la Function

Ejemplo:

```bash
curl -i -X DELETE "https://fn-veterinaria-citas-java.azurewebsites.net/api/rolesJava?id=22"
```

### Paso 3 - Confirmar el contrato HTTP

Revisar:

* Método HTTP.
* Ruta.
* Query parameters.
* Body.
* Headers.

### Paso 4 - Validar Oracle

Si la Function responde correctamente pero la operación de persistencia falla, el siguiente punto de análisis es la conexión con Oracle y el Wallet.

---

# 11. Despliegue

El proyecto se construye utilizando Maven.

```powershell
.\mvnw.cmd clean package
```

El artefacto generado se utiliza posteriormente para el despliegue de Azure Functions.

La aplicación utiliza:

```text
pom.xml
host.json
```

y la configuración propia de Azure Functions.

---

# 12. Seguridad

El repositorio no debe contener:

* Contraseñas.
* Tokens.
* Connection Strings sensibles.
* Oracle Wallet.
* Certificados.
* Claves privadas.

El `.gitignore` contiene exclusiones para archivos sensibles:

```text
wallet/
Wallet/
*.p12
*.jks
*.sso
*.ora
.env
```

La configuración sensible debe administrarse mediante configuración del entorno.

---

# 13. Run Book rápido

## Compilar

```powershell
.\mvnw.cmd clean package
```

## Revisar Git

```powershell
git status
```

## Ver Functions

```powershell
Get-ChildItem .\src\main\java\cl\veterinaria\functions -Recurse
```

## Revisar logs de Azure Functions

Utilizar los mecanismos de logging disponibles en Azure para verificar:

```text
Request
    ↓
Function
    ↓
Oracle
    ↓
Response
```

## Validar endpoints

Usuarios:

```http
GET /api/usuariosJava
POST /api/usuariosJava
PUT /api/usuariosJava
DELETE /api/usuariosJava?id={id}
```

Roles:

```http
GET /api/rolesJava
POST /api/rolesJava
PUT /api/rolesJava
DELETE /api/rolesJava?id={id}
```

---

# 14. Estado final

La solución queda organizada de la siguiente manera:

```text
                 ┌──────────────────┐
                 │     Cliente      │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │       BFF        │
                 │   Spring Boot    │
                 └────────┬─────────┘
                          │
                 ┌────────┴────────┐
                 │                 │
                 ▼                 ▼
        ┌────────────────┐ ┌────────────────┐
        │ usuariosJava   │ │   rolesJava    │
        │ Azure Function │ │ Azure Function │
        └───────┬────────┘ └───────┬────────┘
                │                  │
                └────────┬─────────┘
                         ▼
                  ┌──────────────┐
                  │    Oracle    │
                  └──────────────┘
```

## Resultado

La arquitectura demuestra:

* Separación por dominios.
* Arquitectura serverless.
* Uso de Azure Functions.
* Persistencia en Oracle.
* Integración mediante BFF.
* Composición de información.
* Configuración por ambiente.
* Uso de Docker y EC2.
* Manejo de Oracle Wallet.
* Troubleshooting por capas.
* Adaptación de contratos HTTP sin modificar innecesariamente los servicios.

El principal criterio aplicado durante la implementación fue:

> **Modificar únicamente la capa responsable del problema, manteniendo estables los componentes que ya funcionan.**

Esto permitió resolver problemas de integración sin aumentar innecesariamente la complejidad de la arquitectura.


---

# 15. Implementación de arquitectura orientada a eventos

Como evolución de la arquitectura inicial, se incorporó un flujo orientado a eventos utilizando **Azure Event Grid** y **Azure Cosmos DB** para registrar la trazabilidad de eventos generados durante la creación de usuarios y roles.

La arquitectura mantiene el BFF como punto de entrada y las Azure Functions como responsables de las operaciones CRUD sobre Oracle.

## Arquitectura actual

```text
                         AWS EC2
                  ┌──────────────────┐
                  │   BFF Spring Boot│
                  └────────┬─────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
       ┌──────────────┐          ┌──────────────┐
       │ usuariosJava │          │   rolesJava  │
       │ Azure Function│         │ Azure Function│
       └───────┬──────┘          └──────┬───────┘
               │                        │
               ▼                        ▼
          ┌─────────┐              ┌─────────┐
          │ Oracle  │              │ Oracle  │
          └────┬────┘              └────┬────┘
               │                        │
               ▼                        ▼
   ┌──────────────────────┐  ┌──────────────────────┐
   │generarEventoUsuario  │  │  generarEventoRol    │
   │        Java          │  │        Java          │
   └──────────┬───────────┘  └──────────┬───────────┘
              │                         │
              └───────────┬─────────────┘
                          ▼
                 ┌─────────────────┐
                 │ Azure Event Grid│
                 │      Topic      │
                 └────────┬────────┘
                          │
               ┌──────────┴──────────┐
               │                     │
               ▼                     ▼
      ┌─────────────────┐   ┌─────────────────┐
      │sub-usuarios-    │   │sub-roles-       │
      │function         │   │function         │
      └────────┬────────┘   └────────┬────────┘
               │                     │
               ▼                     ▼
      ┌─────────────────┐   ┌─────────────────┐
      │eventoUsuarioJava│   │eventoRolJava    │
      │ EventGridTrigger│   │ EventGridTrigger│
      └────────┬────────┘   └────────┬────────┘
               │                     │
               └──────────┬──────────┘
                          ▼
                  ┌────────────────┐
                  │   Cosmos DB    │
                  │  trazabilidad  │
                  └────────────────┘