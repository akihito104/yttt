# yttt

live streaming timetable for you

## setup 

- register for YouTube or Twitch and subscribe some channels as you like.
- if you want to connect YouTube Data API, follow [YouTube Data API quickstart](https://developers.google.com/youtube/v3/quickstart/android) to turn on the API.
- if you want to connect Twitch API, follow [get started with the Twitch API](https://dev.twitch.tv/docs/api/get-started/). 
then, create `twitch.properties` and add redirect URL and client ID to the properties file as following: 
  ```properties
  twitch_redirect_uri=<redirect url>
  twitch_client_id=<client id>
  ```
