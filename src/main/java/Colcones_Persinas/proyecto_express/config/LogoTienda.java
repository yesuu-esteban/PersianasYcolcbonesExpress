package Colcones_Persinas.proyecto_express.config;

import java.util.Base64;

/**
 * Logo de P.C Express (PNG 224×224, fondo transparente) guardado dentro del código.
 * Se sirve en /tienda/logo.png. Va aquí y no en la carpeta static para que siempre
 * cargue, sin depender de que se copien o recompilen archivos de imagen.
 */
public final class LogoTienda {

    private LogoTienda() {}

    private static final String BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAOAAAADgCAMAAAAt85rTAAABgFBMVEXcnZwkICDsDAvaGiHcamqcEhYSBQQGAQELAABrAwPj3+Dz" +
            "BgavBQRPBQafo6HeO0OmZWNbY2XkfILbQzxZBQZygnvtvcKhDQ+dERFYBga2xLu1u8XYhHzOxbp3fIKchHu+0svXDRbwWDH///8A" +
            "AH8AVVV/AH9+k4e6O0G0Qz6qVVWAf4r/AFX/AP//f3/9/f0AAAD2AwMBAQH9AAD26efXBwr01dLXR0jwubXql5XVFRbup6XlFBbX" +
            "Jynyycjnh4bmeHbS2NfmaGrWNjflSEv449vkJymyt7Z0d3f4AwPlVldnaGjIyMgXFhdUV1e2xsSIh4ipqarYVlX4BAT4BATxw7nX" +
            "Z2nlNjjng3xHSEiTl5X3BgVwCQquDBF4hIcvAgIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADERRSRAAAAYHRSTlP+8CL7/vkjYKAJ/m0G" +
            "9//////+/p3//6VgYP///v////+WBgECAwL///8G/wMBAv4A/v4D/v7+/v7+/v7+/v7+/v/+/v7+/v//zv7//////////o+x/v7/" +
            "/v//Tv37//useVTHAAAbWUlEQVR42t2dB3fiyLKAm2B7jAMee3fu5t17X44yVkAiSEJEA7YBG4zDeP7/v3hd3a2cWoDH8Oqc3Rkz" +
            "WPCpqit1ELp8d9nfPzg4+IQl5wj8hF/cv9x/909H74oGYLnc1dW3bycnJ68eOTn59u3q6gpQMef+DgLuYzQC9vq69/WrLFcCUqvJ" +
            "X/f2GChg7u8GoMjgcoC29zXEFRYZg76eEMqDfXHrAf/lEuA42byYmJJCbi3gIVXd1bfXvb1aZRXBkFiTm2bcoAYPPmHVRWiu1mzP" +
            "ipZpGppWx6JphmGa1rDT7jUiIF9PNsuINqQ8SudXXaNZtYz6XOkKkSIpuqqdW+1mLcgIetyUa0WbcZk5TOf7ls3hOdIVgUcUVTM7" +
            "BR8kMG5IjWh9t7mPlbfntcze0FC7QjZR1OWw6bmIjMcjVuPHA2K/crK35zHLjqmWhNVEqRfbjYAa9z8YENumx2cWZpourCdzo+pe" +
            "r0YQxQ8CPKR4rll1jHXpqO+ZG2Wvpa6pRbQG3qcT1222zS/CxkSqL5zxWNtbz1DRqnj7n1zt1WZaV9isKEZ1M4hoRdfp4smFoiq8" +
            "g0j1Yc2DuGo2jlazzpNX+8N7piK8l+gL+1Pk16vcwXfT4P6nV1t7zXfEI4hWjX3U1xW9TVbAfVCfnbS8N55fi2Cn7w4oXu7nbPXV" +
            "LF34HvJlZg9FrMT3BaTqY8nGbC58L1EvHCVmHokom3f5xJyL3K4L31Eko2nnNllHIsrC54y+hikJ31eUoWxHjGyEWTR4YKuvqgrf" +
            "X1Bbtt3pwTsA4rv2iaXVNUMSPkKUomyb6cG/bhpw/3LfNs+LufBRUm/avubThgGPsXmy21eUhI8TZcbM9Ftuf5OAosPXqwsfKxpz" +
            "Aye8hIiL7xOr2auK8NGitu3k9GBzGvyB8slWV/h4sc10j4+QB/AH5l40YStEMhnht4NNAIqXP9DrNVVhW4QNxK88hIjXPtu6sD2i" +
            "NrkJkwEPHf0NFWGbRG/zEqZp8AdiDfKHRr9IV1PlHIcoZfxRPkvYOulSZ5qqQ8TBVykKWyilGZeVoqTyiMYHeSv5cLjg0iFK4Pt5" +
            "q/mAkOlwfxVAh88Utla6zNMk5aUolm+fxr+FIGw94WuOTMJmAcTv39te/+KJFnSe5vVTVg2Kl3+j5YO03YCC3qTV00E2wEOWwLQV" +
            "YdtFLSQHfBRdwP9M+JpzYfulXkt0pShSf4fEgdbqwi6IQZSx9yna0aD4AShrwm7IwnY0+3yA4uXPROvWjvAJUjXB0aCIAXhIdF7t" +
            "7gqgoPfIMMzxAYo0AjYVYXdEtY30MB3wmA3AurBLQto0YKSHaYAiM1Brp/jYMMRGKqYC0hS0rewWoDAvxMQKFDTQn0gEVIVdExIN" +
            "ayf7yRo8FA9JhDB3jo8Vh3uhugJFhfhHafcAmZG+HgTCPYow0B3zoI6R0oUKAT+D/B6msIMe1O9JsZ85jgNkCmwquwlIw7184l9/" +
            "6QE8PD4sbNEcywpiRYQK5PUwP+1EEZ/QwCDl/et+tAZFkSpQFXZXDKZCMQqQjcDhDvMJXZiTafhU6NEgUWBtvsuAggb5zFfvKESu" +
            "Ao+2vg3KESrKbBQeBgHZCKzpuw0o1ElK+oMbCx1AqsCisOvCVCiGAR9gfOo7D1gnhaFb+SJ7BB7vcJIWGoWejBTZCvw7AG6LC5Va" +
            "RFZKOTQSC52iAjE+osBO5G+gWZVbZjNTU9fJZVv959HTU+6KyM3T0+h5nBGzC+mM/IOtQuRxMZFlEpsLzyC15mzFTT79gY3mldzT" +
            "c8vzpsETxk66ypKo0I4UDPCfwcW0pbh+VWYpVLWsehwPIuCY3NxOHRXfwAvjhAvpEO/kn1mkQHQyIg/fyohT+Eo7cpvLLIjTp6tk" +
            "yT3TNz6TnyZJ1yKt/BOvkzm+vI+LEapcWVma3EuDp7mrdMkRLd6Svz+lBvuvP1MbRSSLOb6OS7PrlXWEb3F3/+mKT57GzEKvbqS0" +
            "lLvCshlEFJiP7cSoawFWCkZ6SBiFh1zudjQAGeX8qr2ZTNjf+ulVk2uiIrHQSBfDasjVRV5I2azz5mnS9/yK1J883UQocyCkuRmW" +
            "zSDHQmN6oefyuoSJfAH15QatiNg4CQ/RXOJVq9RGRQZIfWjMcJEW6xImJPCtXMBTxqn7OYh40+K0UWRH+XbctaX6wklUHiuV3kU1" +
            "k3TKZ7ETjf2cf4AlWLM0CBA+J9oo7K/6SmI9sgsJjm69JOi1Tc5rT/0uspWSCPiVmJjMCB0n1qMUCw3emQ12vaf8XiPC3eakVBv9" +
            "GwxCAHyBoNzlA6zEAt71+31pZb5nnl+ZeH9jnDhR4dgoYkEi1tchjQj+Ayc6CgHsamFB6BQLQu4Lvn8OZ219n/Of8t0UL2Fitia0" +
            "qY1iQFHMPyS1s2FHlGwvO6SAvvQNTjOS5fbZ6S/9u/70rGzv7vV70lCW1FqFz0d4m97khmQG0SEY32xyI70L6P3uj49yYYbQ+A5k" +
            "PP5dG9bkx8dQrAgO8dxKfLhY4hyEJB+FQYjoELyQkluNIOcRgPLi9ylCrbu7X365Y9JCxak6lJPD/Sibf/HIrfNr/dQuPh6EIqJR" +
            "MClIzFwN6kETHaLTX1ul1l2fAf765a5VKv16iobBpFSJdTDRlYGiamgJxwjVdZ//k3J898UOFIhGQS0ts4sx0WX/11Kp1frrzhHo" +
            "pZTu+lrwOKNl3ACMyEmUerFaqLkdgqH3kJNp8o3xDcKfMOBlHieiBT21BxANeNan7SGHb0wAW/1ZcBR24gw0FCC+LEIJvlyralLQ" +
            "SJOzNY1FQkR8TDlxwHYvYgHb4xIBxCpsjf/6iwCSV8qh6te10X6Sgc6xk6rIzerQMg3TXMzKzH7kto04vuHO1vYA8CW9oa0GxqB7" +
            "ZzUJA5WIznAYnII+S/AzTSV8SkDO5Z7i47Vi4bDTWXoac5KuLqvU3st1vwEkZmtSm3oZRHzMeYrrsmQ6ioKA2PlKhO9u2v/l999/" +
            "758SjZbsqtorZqSH8X9JtSk3zXnYnOYmOV+GbWC0h3ByoIBBIkMmA3lMWoLZxdePCPQyqddLwDdGi9msiFrTu1JJcrJBX/siMgT6" +
            "FGjIctzpGAoivTK6BXXCk62Z1Mug/37gmVNSF9ZCtQGVomWZi2LROmffpjVtKWixWJwhbKps6MOb8NssR1CUAm/9HcpqwrFCerFS" +
            "aciPukeFEw4vg0TsRHu8S0MVf7Jtm8i45BiLVBqnXMM3Ar3BevHVTP4eRs1eRXfGka3NGWA+tmWfBihN+/ZMgq9uY7AxtcU4rvFg" +
            "ppeaZF9kG9+Fbo6jt9ZzAa2VAFunv0ogQssHiH+CV0t3p63kZNJvYxpPKU0Ii+5V+mkFRWEfvcS0tJ1yyTCWy6VhGL+5Y9Aw4SXU" +
            "7yskCI5bOjLhPefmOdLHYxr7+1O0XMIbl/AH/hcp5GI8oVrdM3juMNklYTijMDFbGxI3iiBKoKQ+uOMyA7movOjjsDfG8usdcvKq" +
            "PQQ1BTD2kaddJduRfhwd5KUy59wyYlfCsTA3mrbS3Kh8jO5T2hVKO65cwjXg+Bdcxk+np6fOm9qn/Wm/D5SnyB8Ke0qoKHcVgPZ4" +
            "zwssUiNtTVKn1SBQyT8BYPLEdV2OqwflKppiuufn/vMZU2HtDP/wfDqd9lE1cIwqXaMZ7UOVPe5WD2QQTa7ZOYgTjZ8QDoON5Gmg" +
            "RWzBW0OYbvL2dto/o4SFs7P+6dvbM2b8LTLblqKH4CLD4ge44UXeN1Z+Qulh0K7pI5Lts+d/m0wmb39Mp4OzPSxng/70j7c3QAyV" +
            "E8VQnu0ECeXfs6wf63AuiNThnv8NAC9SrJnV9BGAzbPJG9bg24+nZOZ59Nw//ePtxzf82lkzMhd9jsxDUablR6AZnu6sAh7374gn" +
            "zlftklUP1YOAhwEH2EzxwDt9Gw1+xIBvk2qomqiHKkE7CkoZj1Cq8q2ok3rcgF9qcYB7/4UV9sdgMPj8efT582cy6wWAZ3vR9eBT" +
            "VKk7z7iCE7vHpsQJ+IC4Fhiavnpw7vrH/8AW+SNM5I0+3z493X4m03qYcLAXXUzkopwoyri+Cn8+z4ol6dEGTL++1JbJLFGwLypj" +
            "E6WAWHtYPjPCweTM11aT2WZuKbJUKmacDoBaludXAPAa8aWi6rlx/sVN1XD6ZoDgMDF5owqkGrRV+IwQMryC9Phu0yLrggwrObv0" +
            "NtYIIP82ECU0N/HL5O108hmEAmKZPA8mdzEzRFFTfFLmk0AMvu+8EUAB+hP9txG2zydipG93QqC8iAWU7GI967wUDhQ833loAxrr" +
            "AFIljE8B8XZ0mpwh9qMA9cyAqr/NmpS2pgMWqx0q1boNqNsveWR4hn3n2dkw9A9V/ObqopsAiLIDNriUwgdoyOFqImrWvnD2559n" +
            "MY8pcFqG/agxiFbRIOIEfEgfg8MwYCRHrdCM47MSnQyYqBQybTKOI16G2IL4JqRnfIB6LXH6jENmUiSgHQf1L6Da3O1ogqaj0Wgw" +
            "neD/50jXk7yME/gBjj705Rt4WWPFJYeTuYdk20ovHNcB9Gznjpz2lPCXbTGlBibhxxEvE9vj6iJ1bMCUVE2qrgU49K5buonKRaVg" +
            "kur2BMMrgEbQL5O5hu0jAXxIz0XVNQBlS4qd2B3ETTi5tcZT1MsoflFPKNk+gpZFarJtrQwoGwld39t0wNvQy8/YomSuyO0CVlPj" +
            "ezs9TESvbkaxE9CBtu8Ae5Ozyenp5M+z0W3OMeAw9ykOXI9cnfgSBTyqcCgcmnVpYSJC2mrCKglfW1RSDcMadi4uLjpD09DQZERX" +
            "+H4OAU71GucpKQpMuuWh8cvR4ZjJDdldhFCohB+FhV8KSK2oCFJSrma7UUWbNWXZ/1CVJVIiTfRJa/IZKCvT89C659iw1J1/gce4" +
            "UEBJVedqlMx9L0fNFPnjxIDG+erexcwyzeJieNHs2dbRuFjqIcCbgdHjPiYMejfXBJB/T6TSqKy5Vi0XHIQKQrqr566i1pcz9uSX" +
            "2gyhgWfNb26ELFle8n4U9EUf/pNMn9W/H6Bv7uUmZqpN0lER+say/Ghq6PT0T2gXDP40rEKWE9ohQXm4RLCQK6kJVyqVul2lJHUV" +
            "yQHsKko3UtI/tX91xbne7HfNKhfw0Gx0LHNpmNawh3m1DLUxBLd7OoWdZNWLZq/XJIKcphP5sReQZq8Q7o5J87phmpr7GCYpx78w" +
            "GRbMmJ3HGvVZzeoy29YqyNSO6CKEpEhfr/GVS8T5BQC7RrnB1oAU9ciI3ucZFzr4LyVrX4M01V4uyTKSxNa26VmrRAHncXHvMXBv" +
            "2p47UWCdl/5V7Bz9ZkWBOJ+/JAuBagrH1ARHqubXIAosqWSHdOeuuJe1riXwLa//V0SX2I3WvvDMn6UD+k4xCe9HuOiGpidSll4n" +
            "2+CzlO5ERboYT+Op6R3AeY0DUIvIdWZhN7OGCm+vcuMUJ3pEAO9Ta/pCdg3q7Xa17J1harTL1aYRykdT1gwmyITsREieMXohgC+p" +
            "BZMWuyDWL541fQtYIOrVYqcrCSYZ7AEVPq/Gxyz9KXa7B9xdkQDm09Ptsm92icNElUKh63czVdKh08Kj8Ka1Cl8/zQ1DLHvIixhQ" +
            "hGQtJRslBVLUit/AFJkDiEhbyAvYIYCdcNm7kpH2U9fjGSSPEe1tBWkViNlswk45Cqg3owTnO24mY1HAwPSZwVY2j2+uVl6z7eNL" +
            "cFFFMgTz9saQ1K5F15uL4sxUCgkkrJ40qRcYgwC4tE1lcrXWMJym/yJZLppnGsxX+Lo4/NUEHuGwdEOvPhIpl8ttRJMiNaI3k2lf" +
            "gff2jBInSSvXosj2D17znhTHCwhbm5HdEfT5KjUq585GOOLJ8zQ2BJ3tdbFrfhUmuu6aqBIlOv7PayDh4zuRp7buX61ope4mtCcp" +
            "JTl5cQDzCQsRFoUGSK0BHTIK+GUP/9RwpWaL62QWsDEyQAgL6dys9znLnlWPed5w8UGmfZ13Aa/j8221xjn54ku2iXtpm5phINKW" +
            "MAyDPA+yHNNgo1us072nO3afUnf23NtbXGmgiE1HzfDWHjk10LMZG1yngvtylrv5RkJwT+fNJAWvNeKus0xioccOIA4Us1iH0Q7W" +
            "gzy5qLOSiwA6jWAltknqHgUQN/hGN9wGDT7gWnQ1CH40PltjCotaRhIP6KRzPkAzoQ2cvE15enuTwSWptg9lRz2AjSasKKZTExBJ" +
            "sOXVnQc9JJdL9swwAKLwRBr72uEt8rlRaK231Pef45FLbXQQCxU9gGI+KZkhNT25AXZX7YKj4GU77NqCo8GIZZ7jqEMscreT6bgl" +
            "SbDcfTyd3AbeM0pNSoiF5v1HHl0nrTKFqEnGKM61yHRYjB/1tSwk+jS9plavWwkPbhrFnbCSAwlr+CY1J5DIDWUW6jkwJ6nlP6Ne" +
            "duHY2WO6Bp3nWtpDOGaQ93lOInHVx1NcWSwP9QLmE2fRlBrsUaXentjqMKVcYsHCbNrb4zrxKZ40ueHFe+JqcMCQehBF0X9o1X1i" +
            "PmpAS8xwl+6iZjtCihGd26U1tMyUp7i3BlyIt32+bMeg3ZjAqVwvyYsRJHc1AnmbFCWrd8haz2mGejPibU/BwWMsCPqO/rtObVzQ" +
            "5pP8Tudz9hPPdHrmb2yosutivCfjvaTX9agpy4V3PEB2/Hwb8ps3uafBNFPbBmr5vBgCBBWmlr1KHb334YCt/vR5cAtrM29vR5Np" +
            "P/PpcZAG34uhsw1pVbi7x/v6s5j8ZQSgKO70+b6BGBF1QiwE+/r/NwX6DjEWOZbMpHhoIiUSMaTAv/hDDv6fL6xIscHJrdugK+Jr" +
            "pui6ElXKPxyKMcdQr6tCSVJ0FdURWpSdE4+q59Bdq1bLZHxr+G9nOM4g/A8gRY0WGKhKFs5W7VMRFfv3Ly6qJH2QtFmz0Sg0qwZl" +
            "UoxOr9ZoNKuBSW0oz/OXMYDgSNcahRK+qXMVIW3oP7OKbLLpsClD8kIzMGvYcU8MVN3ZEPrKueA8MNI5hVhtR+zPF+iDtLwj0H8U" +
            "PFEhWkuB2GowoHfnIDn2uVppQBoxb1QaZJmLp2Qmq2V1T3VCYpVnaxes4nE3MVYKc8F+XBbMWVUe9fAIPI4D5IqFiQpUdIyHTPj8" +
            "Rq9XKPQKMyfJU4neSKIHe0MbhUJBZoctkeq4UGjYmRJ5Z6PZK/SaNXwB8iyJ2sww21QBpItSKy6LPcrrj4Hxh/mTdIZ3oVQkYVfX" +
            "8RDUyPdRabtUchoYwzIcDYS/Lj3ycoHvxpAdxAIWOsNvXjJAjSzkcy5Qt4sYyTDsbLoAptw99xvcgiQxCYCi+LDGwwqwiY4RqmvL" +
            "ZiXgjyW7fuxIdgeYtIDBVKuU3+4YkM8fVnxdMAubomfskBW6DTW6xXmU9LwJWheufJIxtk+1riGzCKb2+JthGPbWa9bWqVRLzkgh" +
            "xfGCrig12O40Cc7MsGynBDuCluQCVfqg+LrkmdusFKwgIoBfi2ISYGpdmDgCsYOpw2FcQ7uQlx090q7Mo+I2IoddSbcYGFhoz9DM" +
            "DtYUHG3jzkrJVTt9Jven7s5Ok9Xuaqiz8hJ8MFEQ8HhlPwOAc0yIznvOvLyT3C69pwLV6Zx9E3xMDbGjX9gKTbIW1BNlNBoVZV9M" +
            "mNtO1bdyFKq5+/85Tn2w1AvfvpnwACxRF4qKDVuBsr1Qk/UQZz57Jd9QE3wHl/TI3E7BaeWwo6C6BkNknWPFZIjtrq9x5CmTYh8N" +
            "Rox0hfO2gU9XIQjC/W8YmobdDTMh9vRxttvBaZXLlY7ui/LkDRL1oUYdUiLHBqV61fe8nS4qBzqRKMpAIwAPIRjOpJWCvEoUGPKh" +
            "MKQahKJut50rvV67Y9XdeQxLA8Om+8GH/guwzVsktoBJqPRnYtjuE75IFRHmi3x63QtNjzIrkBgosuCDh/P5F3WukvsL50DIBgmF" +
            "lh2s2oqbajMfWrTbIcRCh3NYVjzXJUF5lIsqjp7kOIkZrHWUrTmERP9DgocRHjT2AYvZPalEfWi9jn6jBtdoVGo1ktkimpCSSXNY" +
            "SENW+3snKTp0G7FhuyGah9bInGRVIlmo3J6VWd5HnjVYa3ce/SdhgZVEGGg0IMnYlBWChIrOi2bPv3e+DvnngrkWiAH1wLPVSFqm" +
            "sW55J9BztXxL3iAZKHpXMNb9OZrI/QzQrOGepDCwbXf5m1XznWgIzWJ6dCPcY/sbep0fVBjgHOkYU3yJtzwXlKLzMyzJFXT35wvX" +
            "BXVgMuKYF5AOQ20VI8Vuxj3FySpKElpYlknyFzjkqYi/In5D0XttDb/DsP+ywIALzzlQMFB1o4qz7vbQPsNTNavNZrO88FSC4SIi" +
            "5UHDMAwLW/R4G0nxL/gt6SUp4KYjB2A8IBmGu/KUU5LCxPDFPws7fx0/q71lfbS2Z66F+3Ht4GjknXjOIlTD1/k4BcYCruRoPkSs" +
            "QJ+QF5A8aWoHnoZtJDiYZEBx21xp7Oz6UQJfAuAlOb+5ud1PYqqTJkX+ciVA2qLZ6ifyqjRAiCsCYs1vNyHlO05CSAakhBfbSggn" +
            "Hd6LifpLA4RwuLU6JHyXKXxpgFtspXx8qYCMUN9K/5lqnzyA1Eqb2/boU402sVP5OACB8N5TPG9N/sLFxwMIecIR7WBuS35tpuRn" +
            "GQHhTuHM29yWFQrd5PphBUAghPpwOypgndR/KfE9I6AdELfB1WiQvvDqjx8QwsV9paFtxfDjcy8ZAelArCw+1kz1Dh1+3HwZANlA" +
            "/FAz1Uj2ecmPlwmQPAv1vlI7/yhvqgxJdMjElw0QLv1yXal+jBJhccpDJvPMDghXB19jlr7/6AP1HR1nU192QFuJ7e+cuZEV/NnV" +
            "twog8TVHlUrxexYYalWuXGcdfasCko/BUb9hfq8qUYeFUferqG9FQPJJLw+VnvE9/CnZfIGtcxX1rQpIEI+PriuZTuhZLTT8Bngv" +
            "l6upb3VAWFCDh+J7Iyqw5mYdvNUB2VAEROO9xqJeJNo7XgNvLUDyuYDYM9/Bo0qqVaDaW4tvLUCGiN1NYVjfbBKuaPC0rPv8mnRr" +
            "A7LPz9+DGucbVB7sEj/aAN76gGQGg6qx0TH0TdCZ5ODTF5Fd+sMBmRr/CUZjo7NU1/GqXUyHTfMBlLcB7W0MkN3rfwBj5dFCq7lV" +
            "XRuSBygwOnEz32xDgB5GeIhM2dL0LJqU5toCVHf98JI/3CDdRgGd75V/uQfIx46pzVN9q6SomtnpEbijF6q74w1+p40CUkaA/Ef+" +
            "5ejhGrYWlIeWoam60vVv5Ol2FR2TWZ1HsrTt+v4on6e/f7jZL7RpQA8kdjsvR/cP19cVGfZQ9B7Lnc6wWBwOO53Hx16PbpK4vn64" +
            "f6Fsl2J+g5b5noCMkX3ZfD7/gkGBFLNWKNX1w8P9PbbIvM0miu8BB/J/ETcCFnRxEhEAAAAASUVORK5CYII=";

    public static final byte[] PNG = Base64.getDecoder().decode(BASE64);
}