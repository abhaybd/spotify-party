# spotify-party
Sync music with others on spotify

If you want to fork this, make sure to generate your own client ID and client secret.
Generate both of them on the Spotify website, and put them in a file called `client.auth`, and place it in `Client\src\main\resources`.
The file should be exactly 2 lines. The first line is the client ID, the second line is the secret. Also, you'll need to add a redirect URI. On the spotify app page, add `http://localhost:8080` to the redirect URI whitelist.
