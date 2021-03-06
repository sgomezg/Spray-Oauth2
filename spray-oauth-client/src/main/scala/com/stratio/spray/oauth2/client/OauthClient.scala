/**
 * Copyright (C) 2016 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.spray.oauth2.client

import spray.client.pipelining._
import spray.http.{HttpResponse, HttpRequest, HttpCookie}
import spray.http.StatusCodes._
import spray.routing._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, ExecutionContext}

trait OauthClient extends HttpService with SessionStore {

  implicit val ec: ExecutionContext = ExecutionContext.global
  val configure = new Config

  import OauthClientHelper._

  def authorizeRedirect = redirect(authorizeRq, Found)

  def indexRedirect = redirect(configure.indexPath, Found)

  def logoutRedirect = redirect(configure.LogoutUrl, Found)



  val secured: Directive1[String] = {
    if (configure.Enabled) {
      optionalCookie(configure.CookieName) flatMap {
        case Some(x) => {
          getSession(x.content) match {
            case Some(cont: String) => provide(cont)
            case None => authorizeRedirect
          }
        }
        case None => authorizeRedirect
      }
    } else {
      val sessionId = getRandomSessionId
      addSession(sessionId, "*")
      setCookie(HttpCookie(configure.CookieName, sessionId, None, None, None, Option("/")))
      provide("*")
    }
  }

  val login = (path("login") & get) {
    parameter("code") { code: String =>
      val (token, expires) = getToken(code)
      val sessionId = getRandomSessionId
      addSession(sessionId, getUserProfile(token))
      setCookie(HttpCookie(configure.CookieName, sessionId, None, Option(expires), None, Option("/"))) {
        indexRedirect
      }
    }
  }

  val logout = path("logout") {
    get {
      optionalCookie(configure.CookieName) {
        case Some(x) => {
          removeSession(x.content)
          deleteCookie(configure.CookieName, path = "/")
          logoutRedirect
        }
        case None => logoutRedirect
      }
    }
  }

  def getToken(code: String): (String, Long) = {
    val tokenResponse: String = makeGetRq(tokenRq(code))
    val (token: String, expires: Long) = parseTokenRs(tokenResponse)
    (token, expires)
  }

  def getUserProfile(token: String): String = {
    val profileUrl = s"${configure.ProfileUrl}?access_token=$token"
    makeGetRq(profileUrl)
  }

  def makeGetRq(url: String): String = {
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    val response = pipeline(Get(url))
    val plainResponse: HttpResponse = Await.result(response, Duration.Inf)
    plainResponse.entity.asString
  }


  val secRoute = login ~ logout
}
