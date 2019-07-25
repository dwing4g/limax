#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#include "../include/QRCODE.h"

#ifdef __APPLE__
#include <malloc/malloc.h>
#else
#include <malloc.h>
#endif

static char *KANJI =
"MAAwATAC/wz/DjD7/xr/G/8f/wEwmzCcALT/QACo/z7/4/8/MP0w/jCdMJ4wA07dMAUwBjAHMPwgFCAQ/w//PDAcIBb/XCAmICUgGCAZIBwgHf8I/wkwFDAV/zv/Pf9b"
"/10wCDAJMAowCzAMMA0wDjAPMBAwEf8LIhIAsQDXMPsA9/8dImD/HP8eImYiZyIeIjQmQiZAALAgMiAzIQP/5f8EAKIAo/8F/wP/Bv8K/yAApyYGJgUlyyXPJc4lxyXG"
"JaEloCWzJbIlvSW8IDswEiGSIZAhkSGTMBMw+zD7MPsw+zD7MPsw+zD7MPsw+zD7IggiCyKGIocigiKDIioiKTD7MPsw+zD7MPsw+zD7MPsiJyIoAKwh0iHUIgAiAzD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsiICKlIxIiAiIHImEiUiJqImsiGiI9Ih0iNSIrIiww+zD7MPsw+zD7MPsw+yErIDAmbyZtJmogICAhALYw+zD7MPsw+yXvMPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7/xD/Ef8S/xP/FP8V/xb/F/8Y/xkw+zD7MPsw+zD7MPsw+/8h/yL/I/8k/yX/Jv8n/yj/Kf8q/yv/LP8t/y7/L/8w"
"/zH/Mv8z/zT/Nf82/zf/OP85/zow+zD7MPsw+zD7MPsw+/9B/0L/Q/9E/0X/Rv9H/0j/Sf9K/0v/TP9N/07/T/9Q/1H/Uv9T/1T/Vf9W/1f/WP9Z/1ow+zD7MPsw+zBB"
"MEIwQzBEMEUwRjBHMEgwSTBKMEswTDBNME4wTzBQMFEwUjBTMFQwVTBWMFcwWDBZMFowWzBcMF0wXjBfMGAwYTBiMGMwZDBlMGYwZzBoMGkwajBrMGwwbTBuMG8wcDBx"
"MHIwczB0MHUwdjB3MHgweTB6MHswfDB9MH4wfzCAMIEwgjCDMIQwhTCGMIcwiDCJMIowizCMMI0wjjCPMJAwkTCSMJMw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MKEwojCjMKQwpTCmMKcwqDCpMKowqzCsMK0wrjCvMLAwsTCyMLMwtDC1MLYwtzC4MLkwujC7MLwwvTC+ML8wwDDBMMIwwzDEMMUwxjDHMMgwyTDKMMswzDDNMM4wzzDQ"
"MNEw0jDTMNQw1TDWMNcw2DDZMNow2zDcMN0w3jDfMPsw4DDhMOIw4zDkMOUw5jDnMOgw6TDqMOsw7DDtMO4w7zDwMPEw8jDzMPQw9TD2MPsw+zD7MPsw+zD7MPsw+wOR"
"A5IDkwOUA5UDlgOXA5gDmQOaA5sDnAOdA54DnwOgA6EDowOkA6UDpgOnA6gDqTD7MPsw+zD7MPsw+zD7MPsDsQOyA7MDtAO1A7YDtwO4A7kDugO7A7wDvQO+A78DwAPB"
"A8MDxAPFA8YDxwPIA8kw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"BBAEEQQSBBMEFAQVBAEEFgQXBBgEGQQaBBsEHAQdBB4EHwQgBCEEIgQjBCQEJQQmBCcEKAQpBCoEKwQsBC0ELgQvMPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"BDAEMQQyBDMENAQ1BFEENgQ3BDgEOQQ6BDsEPAQ9MPsEPgQ/BEAEQQRCBEMERARFBEYERwRIBEkESgRLBEwETQROBE8w+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+yUA"
"JQIlDCUQJRglFCUcJSwlJCU0JTwlASUDJQ8lEyUbJRclIyUzJSslOyVLJSAlLyUoJTclPyUdJTAlJSU4JUIw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+06c"
"VRZaA5Y/VMBhG2MoWfaQIoR1gxx6UGCqY+FuJWXthGaCppv1aJNXJ2WhYnFbm1nQhnuY9H1ifb6bjmIWfJ+It1uJXrVjCWaXaEiVx5eNZ09O5U8KT01PnVBJVvJZN1nU"
"WgFcCWDfYQ9hcGYTaQVwunVPdXB5+32tfe+Aw4QOiGOLApBVkHpTO06VTqVX34CykMF4704AWPFuopA4ejKDKIKLnC9RQVNwVL1U4VbgWftfFZjybeuA5IUtMPsw+zD7"
"lmKWcJagl/tUC1PzW4dwz3+9j8KW6FNvnVx6uk4ReJOB/G4mVhhVBGsdhRqcO1nlU6ltZnTclY9WQk6RkEuW8oNPmQxT4VW2WzBfcWYgZvNoBGw4bPNtKXRbdsh6Tpg0"
"gvGIW4pgku1tsnWrdsqZxWCmiwGNipWyaY5TrVGGMPtXElgwWURbtF72YChjqWP0bL9vFHCOcRRxWXHVcz9+AYJ2gtGFl5BgkludG1hpZbxsWnUlUflZLlllX4Bf3GK8"
"ZfpqKmsna7Rzi3/BiVadLJ0OnsRcoWyWg3tRBFxLYbaBxmh2cmFOWU/6U3hgaW4pek+X804LUxZO7k9VTz1PoU9zUqBT71YJWQ9awVu2W+F50WaHZ5xntmtMbLNwa3PC"
"eY15vno8e4eCsYLbgwSDd4Pvg9OHZoqyVimMqI/mkE6XHoaKT8Rc6GIRcll1O4Hlgr2G/ozAlsWZE5nVTstPGonjVt5YSljKXvtf62AqYJRgYmHQYhJi0GU5MPsw+zD7"
"m0FmZmiwbXdwcHVMdoZ9dYKlh/mVi5aOjJ1R8VK+WRZUs1uzXRZhaGmCba94jYTLiFeKcpOnmrhtbJmohtlXo2f/hs6SDlKDVodUBF7TYuFkuWg8aDhru3NyeLp6a4ma"
"idKNa48DkO2Vo5aUl2lbZlyzaX2YTZhOY5t7IGorMPtqf2i2nA1vX1JyVZ1gcGLsbTtuB27RhFuJEI9EThScOVP2aRtqOpeEaCpRXHrDhLKR3JOMVludKGgigwWEMXyl"
"UgiCxXTmTn5Pg1GgW9JSClLYUudd+1WaWCpZ5luMW5hb215yXnlgo2EfYWNhvmPbZWJn0WhTaPprPmtTbFdvIm+Xb0V0sHUYduN3C3r/e6F8IX3pfzZ/8ICdgmaDnomz"
"isyMq5CElFGVk5WRlaKWZZfTmSiCGE44VCtcuF3Mc6l2THc8XKl/640LlsGYEZhUmFhPAU8OU3FVnFZoV/pZR1sJW8RckF4MXn5fzGPuZzpl12XiZx9oy2jEMPsw+zD7"
"al9eMGvFbBdsfXV/eUhbY3oAfQBfvYmPihiMtI13jsyPHZjimg6bPE6AUH1RAFmTW5xiL2KAZOxrOnKgdZF5R3+ph/uKvItwY6yDypegVAlUA1WraFRqWIpweCdndZ7N"
"U3RbooEahlCQBk4YTkVOx08RU8pUOFuuXxNgJWVRMPtnPWxCbHJs43B4dAN6dnquewh9Gnz+fWZl53JbU7tcRV3oYtJi4GMZbiCGWooxjd2S+G8BeaabWk6oTqtOrE+b"
"T6BQ0VFHevZRcVH2U1RTIVN/U+tVrFiDXOFfN19KYC9gUGBtYx9lWWpLbMFywnLtd++A+IEFggiFTpD3k+GX/5lXmlpO8FHdXC1mgWltXEBm8ml1c4loUHyBUMVS5FdH"
"Xf6TJmWkayNrPXQ0eYF5vXtLfcqCuYPMiH+JX4s5j9GR0VQfkoBOXVA2U+VTOnLXc5Z36YLmjq+ZxpnImdJRd2Eahl5VsHp6UHZb05BHloVOMmrbkedcUVxIMPsw+zD7"
"Y5h6n2yTl3SPYXqqcYqWiHyCaBd+cGhRk2xS8lQbhauKE3+kjs2Q4VNmiIh5QU/CUL5SEVFEVVNXLXPqV4tZUV9iX4RgdWF2YWdhqWOyZDplbGZvaEJuE3Vmej18+31M"
"fZl+S39rgw6DSobNigiKY4tmjv2YGp2PgriPzpvoMPtSh2IfZINvwJaZaEFQkWsgbHpvVHp0fVCIQIojZwhO9lA5UCZQZVF8UjhSY1WnVw9YBVrMXvphsmH4YvNjcmkc"
"ailyfXKscy54FHhvfXl3DICpiYuLGYzijtKQY5N1lnqYVZoTnnhRQ1OfU7Nee18mbhtukHOEc/59Q4I3igCK+pZQTk5QC1PkVHxW+lnRW2Rd8V6rXydiOGVFZ69uVnLQ"
"fMqItIChgOGD8IZOioeN6JI3lseYZ58TTpROkk8NU0hUSVQ+Wi9fjF+hYJ9op2qOdFp4gYqeiqSLd5GQTl6byU6kT3xPr1AZUBZRSVFsUp9SuVL+U5pT41QRMPsw+zD7"
"VA5ViVdRV6JZfVtUW11bj13lXedd9154XoNeml63XxhgUmFMYpdi2GOnZTtmAmZDZvRnbWghaJdpy2xfbSptaW4vbp11MnaHeGx6P3zgfQV9GH1efbGAFYADgK+AsYFU"
"gY+CKoNSiEyIYYsbjKKM/JDKkXWScXg/kvyVpJZNMPuYBZmZmtidO1JbUqtT91QIWNVi92/gjGqPX565UUtSO1RKVv16QJF3nWCe0nNEbwmBcHURX/1g2pqoctuPvGtk"
"mANOylbwV2RYvlpaYGhhx2YPZgZoOWixbfd11X06gm6bQk6bT1BTyVUGXW9d5l3uZ/tsmXRzeAKKUJOWiN9XUF6nYytQtVCsUY1nAFTJWF5Zu1uwX2liTWOhaD1rc24I"
"cH2Rx3KAeBV4JnltZY59MIPciMGPCZabUmRXKGdQf2qMoVG0V0KWKlg6aYqAtFSyXQ5X/HiVnfpPXFJKVItkPmYoZxRn9XqEe1Z9IpMvaFybrXs5UxlRilI3MPsw+zD7"
"W99i9mSuZOZnLWu6hamW0XaQm9ZjTJMGm6t2v2ZSTglQmFPCXHFg6GSSZWNoX3Hmc8p1I3uXfoKGlYuDjNuReJkQZaxmq2uLTtVO1E86T39SOlP4U/JV41bbWOtZy1nJ"
"Wf9bUFxNXgJeK1/XYB1jB2UvW1xlr2W9ZehnnWtiMPtre2wPc0V5SXnBfPh9GX0rgKKBAoHziZaKXoppimaKjIrujMeM3JbMmPxrb06LTzxPjVFQW1db+mFIYwFmQmsh"
"bstsu3I+dL111HjBeTqADIAzgeqElI+ebFCef18Pi1idK3r6jvhbjZbrTgNT8Vf3WTFayVukYIluf28Gdb6M6lufhQB74FByZ/SCnVxhhUp+HoIOUZlcBGNojWZlnHFu"
"eT59F4AFix2OypBuhseQqlAfUvpcOmdTcHxyNZFMkciTK4LlW8JfMWD5TjtT1luIYktnMWuKculz4HougWuNo5FSmZZRElPXVGpb/2OIajl9rJcAVtpTzlRoMPsw+zD7"
"W5dcMV3eT+5hAWL+bTJ5wHnLfUJ+TX/Sge2CH4SQiEaJcouQjnSPL5AxkUuRbJbGkZxOwE9PUUVTQV+TYg5n1GxBbgtzY34mkc2Sg1PUWRlbv23ReV1+LnybWH5xn1H6"
"iFOP8E/KXPtmJXeseuOCHJn/UcZfqmXsaW9riW3zMPtulm9kdv59FF3hkHWRh5gGUeZSHWJAZpFm2W4aXrZ90n9yZviFr4X3ivhSqVPZWXNej1+QYFWS5JZkULdRH1Ld"
"UyBTR1PsVOhVRlUxVhdZaFm+WjxbtVwGXA9cEVwaXoReil7gX3Bif2KEYttjjGN3ZgdmDGYtZnZnfmiiah9qNWy8bYhuCW5YcTxxJnFndcd3AXhdeQF5ZXnweuB7EXyn"
"fTmAloPWhIuFSYhdiPOKH4o8ilSKc4xhjN6RpJJmk36UGJacl5hOCk4ITh5OV1GXUnBXzlg0WMxbIl44YMVk/mdhZ1ZtRHK2dXN6Y4S4i3KRuJMgVjFX9Jj+MPsw+zD7"
"Yu1pDWuWce1+VIB3gnKJ5pjfh1WPsVw7TzhP4U+1VQdaIFvdW+lfw2FOYy9lsGZLaO5pm214bfF1M3W5dx95XnnmfTOB44KvhaqJqoo6jquPm5Aykd2XB066TsFSA1h1"
"WOxcC3UaXD2BTooKj8WWY5dteyWKz5gIkWJW81OoMPuQF1Q5V4JeJWOobDRwindhfIt/4IhwkEKRVJMQkxiWj3RemsRdB11pZXBnoo2olttjbmdJaRmDxZgXlsCI/m+E"
"ZHpb+E4WcCx1XWYvUcRSNlLiWdNfgWAnYhBlP2V0Zh9mdGjyaBZrY24FcnJ1H3bbfL6AVljwiP2Jf4qgipOKy5AdkZKXUpdZZYl6DoEGlrteLWDcYhplpWYUZ5B383pN"
"fE1+PoEKjKyNZI3hjl94qVIHYtljpWRCYpiKLXqDe8CKrJbqfXaCDIdJTtlRSFNDU2Bbo1wCXBZd3WImYkdksGgTaDRsyW1FbRdn029ccU5xfWXLen97rX3aMPsw+zD7"
"fkp/qIF6ghuCOYWmim6Mzo31kHiQd5KtkpGVg5uuUk1VhG84cTZRaHmFflWBs3zOVkxYUVyoY6pm/mb9aVpy2XWPdY55DnlWed98l30gfUSGB4o0ljuQYZ8gUOdSdVPM"
"U+JQCVWqWO5ZT3I9W4tcZFMdYONg82NcY4NjP2O7MPtkzWXpZvld42nNaf1vFXHlTol16Xb4epN8333PfZyAYYNJg1iEbIS8hfuIxY1wkAGQbZOXlxyaElDPWJdhjoHT"
"hTWNCJAgT8NQdFJHU3Ngb2NJZ19uLI2zkB9P11xejMplz32aU1KIllF2Y8NbWFtrXApkDWdRkFxO1lkaWSpscIpRVT5YFVmlYPBiU2fBgjVpVZZAmcSaKE9TWAZb/oAQ"
"XLFeL1+FYCBhS2I0Zv9s8G7egM6Bf4LUiIuMuJAAkC6Wip7bm9tO41PwWSd7LJGNmEyd+W7dcCdTU1VEW4ViWGKeYtNsom/vdCKKF5Q4b8GK/oM4UeeG+FPqMPsw+zD7"
"U+lPRpBUj7BZaoExXf166o+/aNqMN3L4nEhqPYqwTjlTWFYGV2ZixWOiZeZrTm3hbltwrXfteu97qn27gD2AxobLipWTW1bjWMdfPmWtZpZqgGu1dTeKx1Akd+VXMF8b"
"YGVmemxgdfR6Gn9ugfSHGJBFmbN7yXVcevl7UYTEMPuQEHnpepKDNlrhd0BOLU7yW5lf4GK9Zjxn8WzohmuId4o7kU6S85nQahdwJnMqgueEV4yvTgFRRlHLVYtb9V4W"
"XjNegV8UXzVfa1+0YfJjEWaiZx1vbnJSdTp3OoB0gTmBeId2ir+K3I2FjfOSmpV3mAKc5VLFY1d29GcVbIhzzYzDk66Wc20lWJxpDmnMj/2TmnXbkBpYWmgCY7Rp+09D"
"byxn2I+7hSZ9tJNUaT9vcFdqWPdbLH0scipUCpHjnbROrU9OUFxQdVJDjJ5USFgkW5peHV6VXq1e918fYIxitWM6Y9Bor2xAeId5jnoLfeCCR4oCiuaORJATMPsw+zD7"
"kLiRLZHYnw5s5WRYZOJldW70doR7G5Bpk9FuulTyX7lkpI9Nj+2SRFF4WGtZKVxVXpdt+36PdRyMvI7imFtwuU8da79vsXUwlvtRTlQQWDVYV1msXGBfkmWXZ1xuIXZ7"
"g9+M7ZAUkP2TTXgleDpSql6mVx9ZdGASUBJRWlGsMPtRzVIAVRBYVFhYWVdblVz2XYtgvGKVZC1ncWhDaLxo33bXbdhub22bcG9xyF9Tddh5d3tJe1R7UnzWfXFSMIRj"
"hWmF5IoOiwSMRo4PkAOQD5QZlnaYLZowldhQzVLVVAxYAlwOYadknm0ed7N65YD0hASQU5KFXOCdB1M/X5dfs22ccnl3Y3m/e+Rr0nLsiq1oA2phUfh6gWk0XEqc9oLr"
"W8WRSXAeVnhcb2DHZWZsjIxakEGYE1RRZseSDVlIkKNRhU5NUeqFmYsOcFhjepNLaWKZtH4EdXdTV2lgjt+W42xdToxcPF8Qj+lTAozRgImGeV7/ZeVOc1FlMPsw+zD7"
"WYJcP5fuTvtZil/Nio1v4XmweWJb54RxcytxsV50X/Vje2SaccN8mE5DXvxOS1fcVqJgqW/DfQ2A/YEzgb+PsomXhqRd9GKKZK2Jh2d3bOJtPnQ2eDRaRn91gq2ZrE/z"
"XsNi3WOSZVdnb3bDckyAzIC6jymRTVANV/lakmiFMPtpc3Fkcv2Mt1jyjOCWapAZh3955HfnhClPL1JlU1pizWfPbMp2fXuUfJWCNoWEj+tm3W8gcgZ+G4OrmcGeplH9"
"e7F4cnu4gId7SGroXmGAjHVRdWBRa5Jibox2epGXmupPEH9wYpx7T5WlnOlWelhZhuSWvE80UiRTSlPNU9teBmQsZZFnf2w+bE5ySHKvc+11VH5BgiyF6Yype8SRxnFp"
"mBKY72M9Zml1anbkeNCFQ4buUypTUVQmWYNeh198YLJiSWJ5YqtlkGvUbMx1snaueJF52H3Lf3eApYirirmMu5B/l16Y22oLfDhQmVw+X65nh2vYdDV3CX+OMPsw+zD7"
"nztnynoXUzl1i5rtX2aBnYPxgJhfPF/FdWJ7RpA8aGdZ61qbfRB2fossT/VfamoZbDdvAnTieWiIaIpVjHle32PPdcV50oLXkyiS8oSchu2cLVTBX2xljG1ccBWMp4zT"
"mDtlT3T2Tg1O2FfgWStaZlvMUaheA16cYBZidmV3MPtlp2ZubW5yNnsmgVCBmoKZi1yMoIzmjXSWHJZET65kq2tmgh6EYYVqkOhcAWlTmKiEeoVXTw9Sb1+pXkVnDXmP"
"gXmJB4mGbfVfF2JVbLhOz3Jpm5JSBlQ7VnRYs2GkYm5xGllufIl83n0blvBlh4BeThlPdVF1WEBeY15zXwpnxE4mhT2ViZZbfHOYAVD7WMF2VninUiV3pYURe4ZQT1kJ"
"ckd7x33oj7qP1JBNT79SyVopXwGXrU/dgheS6lcDY1VraXUriNyPFHpCUt9Yk2FVYgpmrmvNfD+D6VAjT/hTBVRGWDFZSVudXPBc710pXpZisWNnZT5luWcLMPsw+zD7"
"bNVs4XD5eDJ+K4DegrOEDITshwKJEooqjEqQppLSmP2c851sTk9OoVCNUlZXSlmoXj1f2F/ZYj9mtGcbZ9Bo0lGSfSGAqoGoiwCMjIy/kn6WMlQgmCxTF1DVU1xYqGSy"
"ZzRyZ3dmekaR5lLDbKFrhlgAXkxZVGcsf/tR4XbGMPtkaXjom1Seu1fLWblmJ2eaa85U6WnZXlWBnGeVm6pn/pxSaF1Opk/jU8hiuWcrbKuPxE+tfm2ev04HYWJugG8r"
"hRNUc2cqm0Vd83uVXKxbxoccbkqE0XoUgQhZmXyNbBF3IFLZWSJxIXJfd9uXJ51haQtaf1oYUaVUDVR9Zg5234/3kpic9Fnqcl1uxVFNaMl9v33sl2KeumR4aiGDAlmE"
"W19r23MbdvJ9soAXhJlRMmcontl27mdiUv+ZBVwkYjt8foywVU9gtn0LlYBTAU5fUbZZHHI6gDaRzl8ld+JThF95fQSFrIozjo2XVmfzha6UU2EJYQhsuXZSMPsw+zD7"
"iu2POFUvT1FRKlLHU8tbpV59YKBhgmPWZwln2m5nbYxzNnM3dTF5UIjVipiQSpCRkPWWxIeNWRVOiE9ZTg6KiY8/mBBQrV58WZZbuV64Y9pj+mTBZtxpSmnYbQtutnGU"
"dSh6r3+KgACESYTJiYGLIY4KkGWWfZkKYX5ikWsyMPtsg210f8x//G3Af4WHuoj4Z2WDsZg8lvdtG31hhD2Rak5xU3VdUGsEb+uFzYYtiadSKVQPXGVnTmiodAZ0g3Xi"
"iM+I4ZHMluKWeF+Lc4d6y4ROY6B1ZVKJbUFunHQJdVl4a3ySloZ63J+NT7ZhbmXFhlxOhk6uUNpOIVHMW+5lmWiBbbxzH3ZCd616HHzngm+K0pB8kc+WdZgYUpt90VAr"
"U5hnl23LcdB0M4HojyqWo5xXnp90YFhBbZl9L5heTuRPNk+LUbdSsV26YBxzsnk8gtOSNJa3lvaXCp6Xn2Jmpmt0UhdSo3DIiMJeyWBLYZBvI3FJfD599IBvMPsw+zD7"
"hO6QI5MsVEKbb2rTcImMwo3vlzJStFpBXspfBGcXaXxplG1qbw9yYnL8e+2AAYB+h0uQzlFtnpN5hICLkzKK1lAtVIyKcWtqjMSBB2DRZ6Cd8k6ZTpicEIprhcGFaGkA"
"bn54l4FVMPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+18M"
"ThBOFU4qTjFONk48Tj9OQk5WTlhOgk6FjGtOioISXw1Ojk6eTp9OoE6iTrBOs062Ts5OzU7ETsZOwk7XTt5O7U7fTvdPCU9aTzBPW09dT1dPR092T4hPj0+YT3tPaU9w"
"T5FPb0+GT5ZRGE/UT99Pzk/YT9tP0U/aT9BP5E/lUBpQKFAUUCpQJVAFTxxP9lAhUClQLE/+T+9QEVAGUENQR2cDUFVQUFBIUFpQVlBsUHhQgFCaUIVQtFCyMPsw+zD7"
"UMlQylCzUMJQ1lDeUOVQ7VDjUO5Q+VD1UQlRAVECURZRFVEUURpRIVE6UTdRPFE7UT9RQFFSUUxRVFFievhRaVFqUW5RgFGCVthRjFGJUY9RkVGTUZVRllGkUaZRolGp"
"UapRq1GzUbFRslGwUbVRvVHFUclR21HghlVR6VHtMPtR8FH1Uf5SBFILUhRSDlInUipSLlIzUjlST1JEUktSTFJeUlRSalJ0UmlSc1J/Un1SjVKUUpJScVKIUpGPqI+n"
"UqxSrVK8UrVSwVLNUtdS3lLjUuaY7VLgUvNS9VL4UvlTBlMIdThTDVMQUw9TFVMaUyNTL1MxUzNTOFNAU0ZTRU4XU0lTTVHWU15TaVNuWRhTe1N3U4JTllOgU6ZTpVOu"
"U7BTtlPDfBKW2VPfZvxx7lPuU+hT7VP6VAFUPVRAVCxULVQ8VC5UNlQpVB1UTlSPVHVUjlRfVHFUd1RwVJJUe1SAVHZUhFSQVIZUx1SiVLhUpVSsVMRUyFSoMPsw+zD7"
"VKtUwlSkVL5UvFTYVOVU5lUPVRRU/VTuVO1U+lTiVTlVQFVjVUxVLlVcVUVVVlVXVThVM1VdVZlVgFSvVYpVn1V7VX5VmFWeVa5VfFWDValVh1WoVdpVxVXfVcRV3FXk"
"VdRWFFX3VhZV/lX9VhtV+VZOVlBx31Y0VjZWMlY4MPtWa1ZkVi9WbFZqVoZWgFaKVqBWlFaPVqVWrla2VrRWwla8VsFWw1bAVshWzlbRVtNW11buVvlXAFb/VwRXCVcI"
"VwtXDVcTVxhXFlXHVxxXJlc3VzhXTlc7V0BXT1dpV8BXiFdhV39XiVeTV6BXs1ekV6pXsFfDV8ZX1FfSV9NYClfWV+NYC1gZWB1YclghWGJYS1hwa8BYUlg9WHlYhVi5"
"WJ9Yq1i6WN5Yu1i4WK5YxVjTWNFY11jZWNhY5VjcWORY31jvWPpY+Vj7WPxY/VkCWQpZEFkbaKZZJVksWS1ZMlk4WT560llVWVBZTllaWVhZYllgWWdZbFlpMPsw+zD7"
"WXhZgVmdT15Pq1mjWbJZxlnoWdxZjVnZWdpaJVofWhFaHFoJWhpaQFpsWklaNVo2WmJaalqaWrxavlrLWsJavVrjWtda5lrpWtZa+lr7WwxbC1sWWzJa0FsqWzZbPltD"
"W0VbQFtRW1VbWltbW2VbaVtwW3NbdVt4ZYhbeluAMPtbg1umW7hbw1vHW8lb1FvQW+Rb5lviW95b5VvrW/Bb9lvzXAVcB1wIXA1cE1wgXCJcKFw4XDlcQVxGXE5cU1xQ"
"XE9bcVxsXG5OYlx2XHlcjFyRXJRZm1yrXLtctly8XLdcxVy+XMdc2VzpXP1c+lztXYxc6l0LXRVdF11cXR9dG10RXRRdIl0aXRldGF1MXVJdTl1LXWxdc112XYddhF2C"
"XaJdnV2sXa5dvV2QXbddvF3JXc1d013SXdZd213rXfJd9V4LXhpeGV4RXhteNl43XkReQ15AXk5eV15UXl9eYl5kXkdedV52XnqevF5/XqBewV7CXshe0F7PMPsw+zD7"
"XtZe417dXtpe217iXuFe6F7pXuxe8V7zXvBe9F74Xv5fA18JX11fXF8LXxFfFl8pXy1fOF9BX0hfTF9OXy9fUV9WX1dfWV9hX21fc193X4Nfgl9/X4pfiF+RX4dfnl+Z"
"X5hfoF+oX61fvF/WX/tf5F/4X/Ff3WCzX/9gIWBgMPtgGWAQYClgDmAxYBtgFWArYCZgD2A6YFpgQWBqYHdgX2BKYEZgTWBjYENgZGBCYGxga2BZYIFgjWDnYINgmmCE"
"YJtglmCXYJJgp2CLYOFguGDgYNNgtF/wYL1gxmC1YNhhTWEVYQZg9mD3YQBg9GD6YQNhIWD7YPFhDWEOYUdhPmEoYSdhSmE/YTxhLGE0YT1hQmFEYXNhd2FYYVlhWmFr"
"YXRhb2FlYXFhX2FdYVNhdWGZYZZhh2GsYZRhmmGKYZFhq2GuYcxhymHJYfdhyGHDYcZhumHLf3lhzWHmYeNh9mH6YfRh/2H9Yfxh/mIAYghiCWINYgxiFGIbMPsw+zD7"
"Yh5iIWIqYi5iMGIyYjNiQWJOYl5iY2JbYmBiaGJ8YoJiiWJ+YpJik2KWYtRig2KUYtdi0WK7Ys9i/2LGZNRiyGLcYsxiymLCYsdim2LJYwxi7mLxYydjAmMIYu9i9WNQ"
"Yz5jTWQcY09jlmOOY4Bjq2N2Y6Njj2OJY59jtWNrMPtjaWO+Y+ljwGPGY+NjyWPSY/ZjxGQWZDRkBmQTZCZkNmUdZBdkKGQPZGdkb2R2ZE5lKmSVZJNkpWSpZIhkvGTa"
"ZNJkxWTHZLtk2GTCZPFk54IJZOBk4WKsZONk72UsZPZk9GTyZPplAGT9ZRhlHGUFZSRlI2UrZTRlNWU3ZTZlOHVLZUhlVmVVZU1lWGVeZV1lcmV4ZYJlg4uKZZtln2Wr"
"Zbdlw2XGZcFlxGXMZdJl22XZZeBl4WXxZ3JmCmYDZftnc2Y1ZjZmNGYcZk9mRGZJZkFmXmZdZmRmZ2ZoZl9mYmZwZoNmiGaOZolmhGaYZp1mwWa5Zslmvma8MPsw+zD7"
"ZsRmuGbWZtpm4GY/ZuZm6WbwZvVm92cPZxZnHmcmZyeXOGcuZz9nNmdBZzhnN2dGZ15nYGdZZ2NnZGeJZ3BnqWd8Z2pnjGeLZ6ZnoWeFZ7dn72e0Z+xns2fpZ7hn5Gfe"
"Z91n4mfuZ7lnzmfGZ+dqnGgeaEZoKWhAaE1oMmhOMPtos2graFloY2h3aH9on2iPaK1olGidaJtog2quaLlodGi1aKBoumkPaI1ofmkBaMppCGjYaSJpJmjhaQxozWjU"
"aOdo1Wk2aRJpBGjXaONpJWj5aOBo72koaSppGmkjaSFoxml5aXdpXGl4aWtpVGl+aW5pOWl0aT1pWWkwaWFpXmldaYFpammyaa5p0Gm/acFp02m+ac5b6GnKad1pu2nD"
"aadqLmmRaaBpnGmVabRp3mnoagJqG2n/awpp+WnyaedqBWmxah5p7WoUaetqCmoSasFqI2oTakRqDGpyajZqeGpHamJqWWpmakhqOGoiapBqjWqgaoRqomqjMPsw+zD7"
"apeGF2q7asNqwmq4arNqrGreatFq32qqatpq6mr7awWGFmr6axJrFpsxax9rOGs3dtxrOZjua0drQ2tJa1BrWWtUa1trX2tha3hreWt/a4BrhGuDa41rmGuVa55rpGuq"
"a6trr2uya7Frs2u3a7xrxmvLa9Nr32vsa+tr82vvMPuevmwIbBNsFGwbbCRsI2xebFVsYmxqbIJsjWyabIFsm2x+bGhsc2ySbJBsxGzxbNNsvWzXbMVs3WyubLFsvmy6"
"bNts72zZbOptH4hNbTZtK209bThtGW01bTNtEm0MbWNtk21kbVpteW1ZbY5tlW/kbYVt+W4VbgpttW3HbeZtuG3Gbext3m3Mbeht0m3Fbfpt2W3kbdVt6m3ubi1ubm4u"
"bhlucm5fbj5uI25rbitudm5Nbh9uQ246bk5uJG7/bh1uOG6CbqpumG7Jbrdu0269bq9uxG6ybtRu1W6PbqVuwm6fb0FvEXBMbuxu+G7+bz9u8m8xbu9vMm7MMPsw+zD7"
"bz5vE273b4Zvem94b4FvgG9vb1tv829tb4JvfG9Yb45vkW/Cb2Zvs2+jb6FvpG+5b8Zvqm/fb9Vv7G/Ub9hv8W/ub9twCXALb/pwEXABcA9v/nAbcBpvdHAdcBhwH3Aw"
"cD5wMnBRcGNwmXCScK9w8XCscLhws3CucN9wy3DdMPtw2XEJcP1xHHEZcWVxVXGIcWZxYnFMcVZxbHGPcftxhHGVcahxrHHXcblxvnHScclx1HHOceBx7HHncfVx/HH5"
"cf9yDXIQchtyKHItcixyMHIycjtyPHI/ckByRnJLclhydHJ+coJygXKHcpJylnKicqdyuXKycsNyxnLEcs5y0nLicuBy4XL5cvdQD3MXcwpzHHMWcx1zNHMvcylzJXM+"
"c05zT57Yc1dzanNoc3BzeHN1c3tzenPIc7NzznO7c8Bz5XPuc950onQFdG90JXP4dDJ0OnRVdD90X3RZdEF0XHRpdHB0Y3RqdHZ0fnSLdJ50p3TKdM901HPxMPsw+zD7"
"dOB043TndOl07nTydPB08XT4dPd1BHUDdQV1DHUOdQ11FXUTdR51JnUsdTx1RHVNdUp1SXVbdUZ1WnVpdWR1Z3VrdW11eHV2dYZ1h3V0dYp1iXWCdZR1mnWddaV1o3XC"
"dbN1w3W1db11uHW8dbF1zXXKddJ12XXjdd51/nX/MPt1/HYBdfB1+nXydfN2C3YNdgl2H3YndiB2IXYidiR2NHYwdjt2R3ZIdkZ2XHZYdmF2YnZodml2anZndmx2cHZy"
"dnZ2eHZ8doB2g3aIdot2jnaWdpN2mXaadrB2tHa4drl2unbCds121nbSdt524Xbldud26oYvdvt3CHcHdwR3KXckdx53JXcmdxt3N3c4d0d3Wndod2t3W3dld393fnd5"
"d453i3eRd6B3nnewd7Z3uXe/d7x3vXe7d8d3zXfXd9p33Hfjd+53/HgMeBJ5JnggeSp4RXiOeHR4hnh8eJp4jHijeLV4qniveNF4xnjLeNR4vni8eMV4ynjsMPsw+zD7"
"eOd42nj9ePR5B3kSeRF5GXkseSt5QHlgeVd5X3laeVV5U3l6eX95inmdeaefS3mqea55s3m5ebp5yXnVeed57HnheeN6CHoNehh6GXogeh95gHoxejt6Pno3ekN6V3pJ"
"emF6Ynppn516cHp5en16iHqXepV6mHqWeql6yHqwMPt6tnrFesR6v5CDesd6ynrNes961XrTetl62nrdeuF64nrmeu168HsCew97CnsGezN7GHsZex57NXsoezZ7UHt6"
"ewR7TXsLe0x7RXt1e2V7dHtne3B7cXtse257nXuYe597jXuce5p7i3uSe497XXuZe8t7wXvMe897tHvGe9176XwRfBR75nvlfGB8AHwHfBN783v3fBd8DXv2fCN8J3wq"
"fB98N3wrfD18THxDfFR8T3xAfFB8WHxffGR8VnxlfGx8dXyDfJB8pHytfKJ8q3yhfKh8s3yyfLF8rny5fL18wHzFfMJ82HzSfNx84ps7fO988nz0fPZ8+n0GMPsw+zD7"
"fQJ9HH0VfQp9RX1LfS59Mn0/fTV9Rn1zfVZ9Tn1yfWh9bn1PfWN9k32JfVt9j319fZt9un2ufaN9tX3Hfb19q349faJ9r33cfbh9n32wfdh93X3kfd59+33yfeF+BX4K"
"fiN+IX4SfjF+H34Jfgt+In5GfmZ+O341fjl+Q343MPt+Mn46fmd+XX5Wfl5+WX5afnl+an5pfnx+e36DfdV+fY+ufn9+iH6Jfox+kn6QfpN+lH6Wfo5+m36cfzh/On9F"
"f0x/TX9Of1B/UX9Vf1R/WH9ff2B/aH9pf2d/eH+Cf4Z/g3+If4d/jH+Uf55/nX+af6N/r3+yf7l/rn+2f7iLcX/Ff8Z/yn/Vf9R/4X/mf+l/83/5mNyABoAEgAuAEoAY"
"gBmAHIAhgCiAP4A7gEqARoBSgFiAWoBfgGKAaIBzgHKAcIB2gHmAfYB/gISAhoCFgJuAk4CagK1RkICsgNuA5YDZgN2AxIDagNaBCYDvgPGBG4EpgSOBL4FLMPsw+zD7"
"louBRoE+gVOBUYD8gXGBboFlgWaBdIGDgYiBioGAgYKBoIGVgaSBo4FfgZOBqYGwgbWBvoG4gb2BwIHCgbqByYHNgdGB2YHYgciB2oHfgeCB54H6gfuB/oIBggKCBYIH"
"ggqCDYIQghaCKYIrgjiCM4JAglmCWIJdglqCX4JkMPuCYoJogmqCa4IugnGCd4J4gn6CjYKSgquCn4K7gqyC4YLjgt+C0oL0gvOC+oOTgwOC+4L5gt6DBoLcgwmC2YM1"
"gzSDFoMygzGDQIM5g1CDRYMvgyuDF4MYg4WDmoOqg5+DooOWgyODjoOHg4qDfIO1g3ODdYOgg4mDqIP0hBOD64POg/2EA4PYhAuDwYP3hAeD4IPyhA2EIoQgg72EOIUG"
"g/uEbYQqhDyFWoSEhHeEa4SthG6EgoRphEaELIRvhHmENYTKhGKEuYS/hJ+E2YTNhLuE2oTQhMGExoTWhKGFIYT/hPSFF4UYhSyFH4UVhRSE/IVAhWOFWIVIMPsw+zD7"
"hUGGAoVLhVWFgIWkhYiFkYWKhaiFbYWUhZuF6oWHhZyFd4V+hZCFyYW6hc+FuYXQhdWF3YXlhdyF+YYKhhOGC4X+hfqGBoYihhqGMIY/hk1OVYZUhl+GZ4ZxhpOGo4ap"
"hqqGi4aMhraGr4bEhsaGsIbJiCOGq4bUht6G6YbsMPuG34bbhu+HEocGhwiHAIcDhvuHEYcJhw2G+YcKhzSHP4c3hzuHJYcphxqHYIdfh3iHTIdOh3SHV4doh26HWYdT"
"h2OHaogFh6KHn4eCh6+Hy4e9h8CH0JbWh6uHxIezh8eHxoe7h++H8ofgiA+IDYf+h/aH94gOh9KIEYgWiBWIIoghiDGINog5iCeIO4hEiEKIUohZiF6IYohriIGIfoie"
"iHWIfYi1iHKIgoiXiJKIroiZiKKIjYikiLCIv4ixiMOIxIjUiNiI2YjdiPmJAoj8iPSI6IjyiQSJDIkKiROJQ4keiSWJKokriUGJRIk7iTaJOIlMiR2JYIleMPsw+zD7"
"iWaJZIltiWqJb4l0iXeJfomDiYiJiomTiZiJoYmpiaaJrImvibKJuom9ib+JwInaidyJ3YnnifSJ+IoDihaKEIoMihuKHYolijaKQYpbilKKRopIinyKbYpsimKKhYqC"
"ioSKqIqhipGKpYqmipqKo4rEis2KworaiuuK84rnMPuK5IrxixSK4IriiveK3orbiwyLB4saiuGLFosQixeLIIszl6uLJosriz6LKItBi0yLT4tOi0mLVotbi1qLa4tf"
"i2yLb4t0i32LgIuMi46LkouTi5aLmYuajDqMQYw/jEiMTIxOjFCMVYxijGyMeIx6jIKMiYyFjIqMjYyOjJSMfIyYYh2MrYyqjL2MsoyzjK6MtozIjMGM5IzjjNqM/Yz6"
"jPuNBI0FjQqNB40PjQ2NEJ9OjROMzY0UjRaNZ41tjXGNc42BjZmNwo2+jbqNz43ajdaNzI3bjcuN6o3rjd+N4438jgiOCY3/jh2OHo4Qjh+OQo41jjCONI5KMPsw+zD7"
"jkeOSY5MjlCOSI5ZjmSOYI4qjmOOVY52jnKOfI6BjoeOhY6EjouOio6TjpGOlI6ZjqqOoY6sjrCOxo6xjr6OxY7IjsuO247jjvyO+47rjv6PCo8FjxWPEo8ZjxOPHI8f"
"jxuPDI8mjzOPO485j0WPQo8+j0yPSY9Gj06PV49cMPuPYo9jj2SPnI+fj6OPrY+vj7eP2o/lj+KP6o/vkIeP9JAFj/mP+pARkBWQIZANkB6QFpALkCeQNpA1kDmP+JBP"
"kFCQUZBSkA6QSZA+kFaQWJBekGiQb5B2lqiQcpCCkH2QgZCAkIqQiZCPkKiQr5CxkLWQ4pDkYkiQ25ECkRKRGZEykTCRSpFWkViRY5FlkWmRc5FykYuRiZGCkaKRq5Gv"
"kaqRtZG0kbqRwJHBkcmRy5HQkdaR35HhkduR/JH1kfaSHpH/khSSLJIVkhGSXpJXkkWSSZJkkkiSlZI/kkuSUJKckpaSk5KbklqSz5K5kreS6ZMPkvqTRJMuMPsw+zD7"
"kxmTIpMakyOTOpM1kzuTXJNgk3yTbpNWk7CTrJOtk5STuZPWk9eT6JPlk9iTw5Pdk9CTyJPklBqUFJQTlAOUB5QQlDaUK5Q1lCGUOpRBlFKURJRblGCUYpRelGqSKZRw"
"lHWUd5R9lFqUfJR+lIGUf5WClYeVipWUlZaVmJWZMPuVoJWolaeVrZW8lbuVuZW+lcpv9pXDlc2VzJXVldSV1pXcleGV5ZXiliGWKJYuli+WQpZMlk+WS5Z3llyWXpZd"
"ll+WZpZylmyWjZaYlpWWl5aqlqeWsZaylrCWtJa2lriWuZbOlsuWyZbNiU2W3JcNltWW+ZcElwaXCJcTlw6XEZcPlxaXGZcklyqXMJc5lz2XPpdEl0aXSJdCl0mXXJdg"
"l2SXZpdoUtKXa5dxl3mXhZd8l4GXepeGl4uXj5eQl5yXqJeml6OXs5e0l8OXxpfIl8uX3Jftn0+X8nrfl/aX9ZgPmAyYOJgkmCGYN5g9mEaYT5hLmGuYb5hwMPsw+zD7"
"mHGYdJhzmKqYr5ixmLaYxJjDmMaY6ZjrmQOZCZkSmRSZGJkhmR2ZHpkkmSCZLJkumT2ZPplCmUmZRZlQmUuZUZlSmUyZVZmXmZiZpZmtma6ZvJnfmduZ3ZnYmdGZ7Znu"
"mfGZ8pn7mfiaAZoPmgWZ4poZmiuaN5pFmkKaQJpDMPuaPppVmk2aW5pXml+aYpplmmSaaZprmmqarZqwmryawJrPmtGa05rUmt6a35rimuOa5prvmuua7pr0mvGa95r7"
"mwabGJsamx+bIpsjmyWbJ5somymbKpsumy+bMptEm0ObT5tNm06bUZtYm3Sbk5uDm5GblpuXm5+boJuom7SbwJvKm7mbxpvPm9Gb0pvjm+Kb5JvUm+GcOpvym/Gb8JwV"
"nBScCZwTnAycBpwInBKcCpwEnC6cG5wlnCScIZwwnEecMpxGnD6cWpxgnGecdpx4nOec7JzwnQmdCJzrnQOdBp0qnSadr50jnR+dRJ0VnRKdQZ0/nT6dRp1IMPsw+zD7"
"nV2dXp1knVGdUJ1ZnXKdiZ2Hnaudb516nZqdpJ2pnbKdxJ3BnbuduJ26ncadz53Cndmd0534nead7Z3vnf2eGp4bnh6edZ55nn2egZ6InouejJ6SnpWekZ6dnqWeqZ64"
"nqqerZdhnsyezp7PntCe1J7cnt6e3Z7gnuWe6J7vMPue9J72nvee+Z77nvye/Z8Hnwh2t58VnyGfLJ8+n0qfUp9Un2OfX59gn2GfZp9nn2yfap93n3Kfdp+Vn5yfoFgv"
"aceQWXRkUdxxmTD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7"
"MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+zD7MPsw+w==";


static int gf_exp[512];
static int gf_log[256];
static unsigned char kanji[16384];

void qr_initialize(){
	gf_exp[0] = 1;
	for (int x = 1, i = 1; i < 255; i++) {
		if (((x <<= 1) & 0x100) != 0)
			x ^= 0x11d;
		gf_exp[i] = x;
		gf_log[x] = i;
	}
	for (int i = 255; i < 512; i++)
		gf_exp[i] = gf_exp[i - 255];
	int map[128];
	for (int i = 0; i < 64; i++)
		map[(int)"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"[i]] = i;
	char *p = KANJI;
	int pos = 0;
	for (int i = 0; i < (int)(sizeof(kanji) / 3); i++) {
		int v = map[(int)*p++] << 18;
		v |= map[(int)*p++] << 12;
		v |= map[(int)*p++] << 6;
		v |= map[(int)*p++];
		kanji[pos++] = (unsigned char)(v >> 16);
		kanji[pos++] = (unsigned char)(v >> 8);
		kanji[pos++] = (unsigned char)v;
	}
	int v = map[(int)*p++] << 2;
	v |= map[(int)*p] >> 4;
	kanji[sizeof(kanji) - 1] = (unsigned char)v;
}

static int gf_mul(int x, int y) {
	return x == 0 || y == 0 ? 0 : gf_exp[gf_log[x] + gf_log[y]];
}

static int gf_div(int x, int y) {
	return x == 0 ? 0 : gf_exp[gf_log[x] + 255 - gf_log[y]];
}

static int rs_correct_msg(char *msg, int msg_length, int nsym, int *workbuffer) {
	int *syndromes = workbuffer;
	int err = 0;
	for (int i = 0; i < nsym; i++) {
		int s = msg[0] & 0xff;
		for (int j = 1; j < msg_length; j++)
			s = gf_mul(s, gf_exp[i]) ^ (msg[j] & 0xff);
		if ((syndromes[i] = s) != 0)
			err = 1;
	}
	if (err) {
		int *err_poly = syndromes + nsym;
		int *old_poly = err_poly + nsym + 1;
		int *tmp_poly = old_poly + nsym + 1;
		err_poly[0] = old_poly[0] = 1;
		int err_poly_length = 1;
		int old_poly_length = 1;
		for (int i = 0; i < nsym; i++) {
			old_poly[old_poly_length++] = 0;
			int delta = syndromes[i];
			for (int j = 1; j < err_poly_length; j++)
				delta ^= gf_mul(err_poly[err_poly_length - 1 - j], syndromes[i - j]);
			if (delta == 0)
				continue;
			int dinv = gf_div(1, delta);
			if (old_poly_length > err_poly_length) {
				for (int j = 0; j < old_poly_length; j++)
					tmp_poly[j] = gf_mul(old_poly[j], delta);
				for (int j = 0; j < err_poly_length; j++)
					old_poly[j] = gf_mul(err_poly[j], dinv);
				int *t0 = err_poly;
				err_poly = tmp_poly;
				tmp_poly = t0;
				int t1 = old_poly_length;
				old_poly_length = err_poly_length;
				err_poly_length = t1;
			}
			int off = err_poly_length - old_poly_length;
			for (int j = 0; j < old_poly_length; j++)
				err_poly[j + off] ^= gf_mul(old_poly[j], delta);
		}
		int errs = err_poly_length - 1;
		if (errs << 1 > nsym)
			return 0;
		int *err_pos = tmp_poly;
		int err_pos_length = 0;
		for (int i = 0; i < msg_length; i++) {
			int x = gf_exp[255 - i];
			int y = err_poly[0];
			for (int j = 1; j < err_poly_length; j++)
				y = gf_mul(y, x) ^ err_poly[j];
			if (y == 0)
				err_pos[err_pos_length++] = msg_length - 1 - i;
		}
		if (err_pos_length != errs)
			return 0;
		int q_length = errs + 1;
		int *q = err_poly;
		q[0] = 1;
		q[1] = 0;
		for (int i = 0; i < errs; i++) {
			int a, b, x = gf_exp[msg_length - 1 - err_pos[i]];
			q[0] = gf_mul(a = q[0], x);
			for (int j = 0; j <= i; j++, a = b)
				q[j + 1] = a ^ gf_mul(b = q[j + 1], x);
			q[i + 2] = 0;
		}
		int *p = syndromes;
		int p_length = errs;
		for (int i = 0; i < p_length >> 1; i++) {
			int t = p[i];
			p[i] = p[p_length - i - 1];
			p[p_length - i - 1] = t;
		}
		int *r = old_poly;
		for (int i = 0; i < p_length + q_length - 1; i++)
			r[i] = 0;
		for (int i = 0; i < p_length; i++)
			for (int j = 0; j < q_length; j++)
				r[i + j] ^= gf_mul(p[i], q[j]);
		p = r;
		for (int i = 0; i < p_length; i++)
			p[i] = r[i + q_length - 1];
		for (int k = q_length & 1, i = 0; i < errs; i++) {
			int x1 = gf_exp[err_pos[i] + 256 - msg_length];
			int x2 = gf_mul(x1, x1);
			int y = p[0];
			for (int j = 1; j < p_length; j++)
				y = gf_mul(y, x1) ^ p[j];
			int z = q[k];
			for (int j = k + 2; j < q_length; j += 2)
				z = gf_mul(z, x2) ^ q[j];
			msg[err_pos[i]] ^= gf_div(y, gf_mul(x1, z));
		}
	}
	return 1;
}

static void squareToQuadrilateral(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float coef[9]) {
	float dx3 = x0 - x1 + x2 - x3;
	float dy3 = y0 - y1 + y2 - y3;
	if (dx3 == 0.0f && dy3 == 0.0f) {
		coef[0] = x1 - x0;
		coef[1] = y1 - y0;
		coef[2] = 0.0f;
		coef[3] = x2 - x1;
		coef[4] = y2 - y1;
		coef[5] = 0.0f;
	}
	else {
		float dx1 = x1 - x2;
		float dx2 = x3 - x2;
		float dy1 = y1 - y2;
		float dy2 = y3 - y2;
		float denominator = dx1 * dy2 - dx2 * dy1;
		float a13 = (dx3 * dy2 - dx2 * dy3) / denominator;
		float a23 = (dx1 * dy3 - dx3 * dy1) / denominator;
		coef[0] = x1 - x0 + a13 * x1;
		coef[1] = y1 - y0 + a13 * y1;
		coef[2] = a13;
		coef[3] = x3 - x0 + a23 * x3;
		coef[4] = y3 - y0 + a23 * y3;
		coef[5] = a23;
	}
	coef[6] = x0;
	coef[7] = y0;
	coef[8] = 1.0f;
}

static void quadrilateralToSquare(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float coef[9]) {
	float r[9];
	squareToQuadrilateral(x0, y0, x1, y1, x2, y2, x3, y3, r);
	coef[0] = r[4] * r[8] - r[5] * r[7];
	coef[1] = r[2] * r[7] - r[1] * r[8];
	coef[2] = r[1] * r[5] - r[2] * r[4];
	coef[3] = r[5] * r[6] - r[3] * r[8];
	coef[4] = r[0] * r[8] - r[2] * r[6];
	coef[5] = r[2] * r[3] - r[0] * r[5];
	coef[6] = r[3] * r[7] - r[4] * r[6];
	coef[7] = r[1] * r[6] - r[0] * r[7];
	coef[8] = r[0] * r[4] - r[1] * r[3];
}

static void quadrilateralToQuadrilateral(float x0, float y0, float x1, float y1, float x2, float y2,
	float x3, float y3, float x0p, float y0p, float x1p, float y1p, float x2p, float y2p, float x3p, float y3p, float* coef) {
	float a[9], b[9];
	quadrilateralToSquare(x0, y0, x1, y1, x2, y2, x3, y3, b);
	squareToQuadrilateral(x0p, y0p, x1p, y1p, x2p, y2p, x3p, y3p, a);
	coef[0] = a[0] * b[0] + a[3] * b[1] + a[6] * b[2];
	coef[1] = a[1] * b[0] + a[4] * b[1] + a[7] * b[2];
	coef[2] = a[2] * b[0] + a[5] * b[1] + a[8] * b[2];
	coef[3] = a[0] * b[3] + a[3] * b[4] + a[6] * b[5];
	coef[4] = a[1] * b[3] + a[4] * b[4] + a[7] * b[5];
	coef[5] = a[2] * b[3] + a[5] * b[4] + a[8] * b[5];
	coef[6] = a[0] * b[6] + a[3] * b[7] + a[6] * b[8];
	coef[7] = a[1] * b[6] + a[4] * b[7] + a[7] * b[8];
	coef[8] = a[2] * b[6] + a[5] * b[7] + a[8] * b[8];
}

static void transform(float *coef, float *points, int length) {
	float a11 = coef[0];
	float a12 = coef[1];
	float a13 = coef[2];
	float a21 = coef[3];
	float a22 = coef[4];
	float a23 = coef[5];
	float a31 = coef[6];
	float a32 = coef[7];
	float a33 = coef[8];
	for (int i = 0; i < length; i += 2) {
		float x = points[i];
		float y = points[i + 1];
		float denominator = a13 * x + a23 * y + a33;
		points[i] = (a11 * x + a21 * y + a31) / denominator;
		points[i + 1] = (a12 * x + a22 * y + a32) / denominator;
	}
}

struct bit_stream {
	void *data;
	int capacity;
	int nbits;
	int pos;
};

static struct bit_stream* bit_stream_alloc() {
	struct bit_stream *bs = (struct bit_stream *)malloc(sizeof(struct bit_stream));
	bs->data = malloc(bs->capacity = 128);
	memset(bs->data, 0, bs->capacity);
	bs->nbits = 0;
	bs->pos = 0;
	return bs;
}

static void bit_stream_free(struct bit_stream *bs) {
	free(bs->data);
	free(bs);
}

static int bit_stream_bytelength(struct bit_stream *bs) {
	return (bs->nbits >> 3) + ((bs->nbits & 7) ? 1 : 0);
}

static void* bit_stream_to_bytearray(struct bit_stream *bs, int *size) {
	*size = bit_stream_bytelength(bs);
	void *data = malloc(*size);
	memcpy(data, bs->data, *size);
	return data;
}

static struct bit_stream* bit_stream_from_bytearray(char *buffer, int size) {
	struct bit_stream *bs = (struct bit_stream *)malloc(sizeof(struct bit_stream));
	bs->data = malloc(bs->capacity = size);
	memcpy(bs->data, buffer, size);
	bs->nbits = size << 3;
	bs->pos = 0;
	return bs;
}

static void bit_stream_append(struct bit_stream *bs, unsigned int val, int len) {
	if (bs->nbits + len > (bs->capacity << 3)) {
		int capacity = bs->capacity;
		bs->data = realloc(bs->data, bs->capacity <<= 1);
		memset((char*)bs->data + capacity, 0, capacity);
	}
	char *p = (char *)bs->data + (bs->nbits >> 3);
	int rem = 8 - (bs->nbits & 7);
	bs->nbits += len;
	val <<= 32 - len;
	int shift = 32 - rem;
	*p |= (char)(val >> shift);
	for (len -= rem; len > 0; len -= 8)
		*++p = (char)(val >> (shift -= 8));
}

static int bit_stream_get(struct bit_stream *bs, int len) {
	if (len + bs->pos > bs->nbits)
		len = bs->nbits - bs->pos;
	if (len == 0)
		return 0;
	unsigned char *p = (unsigned char *)bs->data + (bs->pos >> 3);
	int rem = 8 - (bs->pos & 7);
	bs->pos += len;
	int mask = 0xfffffffful >> (32 - len);
	int v;
	if (len <= rem) {
		v = *p >> (rem - len);
	}
	else{
		for (v = *p++, len -= rem; len >= 8; len -= 8)
			v = (v << 8) | *p++;
		if (len > 0)
			v = (v << len) | (*p >> (8 - len));
	}
	return v & mask;
}

static int errorCorrectionCharacteristics[40][9] = {
	{ 26, 1, 1, 1, 1, 10, 7, 17, 13 }, { 44, 1, 1, 1, 1, 16, 10, 28, 22 },
	{ 70, 1, 1, 2, 2, 26, 15, 22, 18 }, { 100, 2, 1, 4, 2, 18, 20, 16, 26 },
	{ 134, 2, 1, 4, 4, 24, 26, 22, 18 }, { 172, 4, 2, 4, 4, 16, 18, 28, 24 },
	{ 196, 4, 2, 5, 6, 18, 20, 26, 18 }, { 242, 4, 2, 6, 6, 22, 24, 26, 22 },
	{ 292, 5, 2, 8, 8, 22, 30, 24, 20 }, { 346, 5, 4, 8, 8, 26, 18, 28, 24 },
	{ 404, 5, 4, 11, 8, 30, 20, 24, 28 }, { 466, 8, 4, 11, 10, 22, 24, 28, 26 },
	{ 532, 9, 4, 16, 12, 22, 26, 22, 24 }, { 581, 9, 4, 16, 16, 24, 30, 24, 20 },
	{ 655, 10, 6, 18, 12, 24, 22, 24, 30 }, { 733, 10, 6, 16, 17, 28, 24, 30, 24 },
	{ 815, 11, 6, 19, 16, 28, 28, 28, 28 }, { 901, 13, 6, 21, 18, 26, 30, 28, 28 },
	{ 991, 14, 7, 25, 21, 26, 28, 26, 26 }, { 1085, 16, 8, 25, 20, 26, 28, 28, 30 },
	{ 1156, 17, 8, 25, 23, 26, 28, 30, 28 }, { 1258, 17, 9, 34, 23, 28, 28, 24, 30 },
	{ 1364, 18, 9, 30, 25, 28, 30, 30, 30 }, { 1474, 20, 10, 32, 27, 28, 30, 30, 30 },
	{ 1588, 21, 12, 35, 29, 28, 26, 30, 30 }, { 1706, 23, 12, 37, 34, 28, 28, 30, 28 },
	{ 1828, 25, 12, 40, 34, 28, 30, 30, 30 }, { 1921, 26, 13, 42, 35, 28, 30, 30, 30 },
	{ 2051, 28, 14, 45, 38, 28, 30, 30, 30 }, { 2185, 29, 15, 48, 40, 28, 30, 30, 30 },
	{ 2323, 31, 16, 51, 43, 28, 30, 30, 30 }, { 2465, 33, 17, 54, 45, 28, 30, 30, 30 },
	{ 2611, 35, 18, 57, 48, 28, 30, 30, 30 }, { 2761, 37, 19, 60, 51, 28, 30, 30, 30 },
	{ 2876, 38, 19, 63, 53, 28, 30, 30, 30 }, { 3034, 40, 20, 66, 56, 28, 30, 30, 30 },
	{ 3196, 43, 21, 70, 59, 28, 30, 30, 30 }, { 3362, 45, 22, 74, 62, 28, 30, 30, 30 },
	{ 3532, 47, 24, 77, 65, 28, 30, 30, 30 }, { 3706, 49, 25, 81, 68, 28, 30, 30, 30 } };

static int getTotalNumberOfCodewords(int version) {
	return errorCorrectionCharacteristics[version - 1][0];
}

static int getNumberOfErrorCorrectionBlocks(int version, int ecl) {
	return errorCorrectionCharacteristics[version - 1][ecl + 1];
}

static int getNumberOfErrorCorrectionCodewordsPerBlock(int version, int ecl) {
	return errorCorrectionCharacteristics[version - 1][ecl + 5];
}

static int getBitCapacity(int version, int ecl) {
	return (getTotalNumberOfCodewords(version) - getNumberOfErrorCorrectionBlocks(version, ecl)
		* getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl)) * 8;
}

static int testBit(int x, int i) {
	return (x & (1 << i)) != 0;
}

static void initializeVersion(int version, char* modules, char* funmask, int size) {
	int n = size - 7;
	for (int i = 0, j = size - 1; i < 7; i++) {
		int k = n + i;
		modules[i] = modules[6 * size + i] = modules[i * size] = modules[i * size + 6] = 1;
		modules[n * size + i] = modules[j * size + i] = modules[i * size + n] = modules[i * size + j] = 1;
		modules[k] = modules[6 * size + k] = modules[k * size] = modules[k * size + 6] = 1;
	}
	for (int i = 2; i < 5; i++)
		for (int j = 2; j < 5; j++)
			modules[j * size + i] = modules[(n + j) * size + i] = modules[j * size + n + i] = 1;
	n--;
	for (int i = 0; i < 8; i++)
		for (int j = 0; j < 8; j++)
			funmask[j * size + i] = funmask[(n + j) * size + i] = funmask[j * size + n + i] = 1;
	for (int i = 8; i < n; i++) {
		modules[6 * size + i] = modules[i * size + 6] = (i & 1) == 0;
		funmask[6 * size + i] = funmask[i * size + 6] = 1;
	}
	if (version > 1) {
		n = version / 7 + 2;
		int s = version == 32 ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
		int r[7];
		for (int i = n, p = version * 4 + 10; --i > 0; p -= s)
			r[i] = p;
		r[0] = 6;
		for (int a = 0; a < n; a++)
			for (int b = 0; b < n; b++) {
				if ((a == 0 && b == 0) || (a == 0 && b == n - 1) || (a == n - 1 && b == 0))
					continue;
				int x = r[b];
				int y = r[a];
				for (int i = -2; i <= 2; i++) {
					for (int j = -2; j <= 2; j++)
						funmask[(y + j) * size + x + i] = 1;
					modules[(y - 2) * size + x + i] = 1;
					modules[(y + 2) * size + x + i] = 1;
					modules[(y + i) * size + x - 2] = 1;
					modules[(y + i) * size + x + 2] = 1;
				}
				modules[y * size + x] = 1;
			}
	}
	if (version > 6) {
		int r = version;
		for (int i = 0; i < 12; i++)
			r = (r << 1) ^ ((r >> 11) * 0x1f25);
		int v = version << 12 | r;
		for (int i = 0; i < 18; i++) {
			int x = i / 3;
			int y = size - 11 + i % 3;
			funmask[y * size + x] = funmask[x * size + y] = 1;
			if (testBit(v, i))
				modules[y * size + x] = modules[x * size + y] = 1;
		}
	}
	for (int i = 0; i < 9; i++)
		funmask[i * size + 8] = funmask[8 * size + i] = 1;
	for (int i = 0; i < 8; i++)
		funmask[8 * size + size - i - 1] = funmask[(size - 8 + i) * size + 8] = 1;
	modules[(size - 8) * size + 8] = 1;
}

static void maskPattern(char* modules, char* funmask, int size, int pattern) {
	for (int p = 0, i = 0; i < size; i++) {
		for (int j = 0; j < size; j++, p++) {
			if (!funmask[p])
				switch (pattern) {
				case 0:
					modules[p] ^= (i + j) % 2 == 0;
					break;
				case 1:
					modules[p] ^= i % 2 == 0;
					break;
				case 2:
					modules[p] ^= j % 3 == 0;
					break;
				case 3:
					modules[p] ^= (i + j) % 3 == 0;
					break;
				case 4:
					modules[p] ^= (i / 2 + j / 3) % 2 == 0;
					break;
				case 5:
					modules[p] ^= i * j % 2 + i * j % 3 == 0;
					break;
				case 6:
					modules[p] ^= (i * j % 2 + i * j % 3) % 2 == 0;
					break;
				case 7:
					modules[p] ^= ((i + j) % 2 + i * j % 3) % 2 == 0;
					break;
			}
		}
	}
}

static int computePenaltyScore(char* modules, int size) {
	int score = 0;
	int dark = 0;
	for (int i = 0; i < size; i++) {
		char xcolor = modules[i * size];
		char ycolor = modules[i];
		int xsame = 1;
		int ysame = 1;
		int xbits = modules[i * size] ? 1 : 0;
		int ybits = modules[i] ? 1 : 0;
		dark += modules[i * size] ? 1 : 0;
		for (int j = 1; j < size; j++) {
			if (modules[i * size + j] != xcolor) {
				xcolor = modules[i * size + j];
				xsame = 1;
			}
			else {
				if (++xsame == 5)
					score += 3;
				else if (xsame > 5)
					score++;
			}
			if (modules[j * size + i] != ycolor) {
				ycolor = modules[j * size + i];
				ysame = 1;
			}
			else {
				if (++ysame == 5)
					score += 3;
				else if (ysame > 5)
					score++;
			}
			xbits = ((xbits << 1) & 0x7ff) | (modules[i * size + j] ? 1 : 0);
			ybits = ((ybits << 1) & 0x7ff) | (modules[j * size + i] ? 1 : 0);
			if (j >= 10) {
				if (xbits == 0x5d || xbits == 0x5d0)
					score += 40;
				if (ybits == 0x5d || ybits == 0x5d0)
					score += 40;
			}
			dark += modules[i * size + j] ? 1 : 0;
		}
	}
	for (int i = 0; i < size - 1; i++)
		for (int j = 0; j < size - 1; j++) {
			char c = modules[i * size + j];
			if (c == modules[i * size + j + 1] && c == modules[(i + 1) * size + j] && c == modules[(i + 1) * size + j + 1])
				score += 3;
		}
	dark *= 20;
	for (int k = 0, total = size * size; dark < total * (9 - k) || dark > total * (11 + k); k++)
		score += 10;
	return score;
}

static void placeErrorCorrectionCodewords(char* modules, char* funmask, int size, char* errorCorrectionCodewords, int length) {
	for (int i = 0, bitLength = length << 3, x = size - 1, y = size - 1, dir = -1; x >= 1; x -= 2, y += (dir = -dir)) {
		if (x == 6)
			x = 5;
		for (; y >= 0 && y < size; y += dir)
			for (int j = 0; j < 2; j++) {
				int p = y * size + x - j;
				if (!funmask[p] && i < bitLength) {
					modules[p] = testBit(errorCorrectionCodewords[i >> 3], 7 - (i & 7));
					i++;
				}
			}
	}
}

static void readErrorCorrectionCodewords(char* modules, char* funmask, int size, char* codewords, int* length) {
	struct bit_stream *bs = bit_stream_alloc();
	for (int i = 0, bitLength = getTotalNumberOfCodewords((size - 17) / 4) << 3, x = size - 1, y = size - 1, dir = -1; x >= 1; x -= 2, y += (dir = -dir)) {
		if (x == 6)
			x = 5;
		for (; y >= 0 && y < size; y += dir)
			for (int j = 0; j < 2; j++) {
				int p = y * size + x - j;
				if (!funmask[p] && i < bitLength) {
					bit_stream_append(bs, modules[p] ? 1 : 0, 1);
					i++;
				}
			}
	}
	memcpy(codewords, bs->data, *length = bit_stream_bytelength(bs));
	bit_stream_free(bs);
}

static void placeMask(char* modules, char* funmask, int size, int ecl, int mask) {
	int v = ecl << 3 | mask;
	int r = v;
	for (int i = 0; i < 10; i++)
		r = (r << 1) ^ ((r >> 9) * 0x537);
	v = ((v << 10) | r) ^ 0x5412;
	for (int i = 0; i < 6; i++)
		modules[i * size + 8] = testBit(v, i);
	modules[7 * size + 8] = testBit(v, 6);
	modules[8 * size + 8] = testBit(v, 7);
	modules[8 * size + 7] = testBit(v, 8);
	for (int i = 9; i < 15; i++)
		modules[8 * size + 14 - i] = testBit(v, i);
	for (int i = 0; i < 8; i++)
		modules[8 * size + size - 1 - i] = testBit(v, i);
	for (int i = 8; i < 15; i++)
		modules[(size - 15 + i) * size + 8] = testBit(v, i);
}

static int selectMaskPattern(char* modules, char* funmask, int size, int ecl) {
	int pattern = 0;
	int minPenaltyScore = INT32_MAX;
	for (int i = 0; i < 8; i++) {
		placeMask(modules, funmask, size, ecl, i);
		maskPattern(modules, funmask, size, i);
		int penaltyScore = computePenaltyScore(modules, size);
		if (penaltyScore < minPenaltyScore) {
			minPenaltyScore = penaltyScore;
			pattern = i;
		}
		maskPattern(modules, funmask, size, i);
	}
	return pattern;
}

static int generateErrorCorrectionCodewords(char* codewords, int codewords_length, int version, int ecl, char* r) {
	int totalNumberOfCodewords = getTotalNumberOfCodewords(version);
	int numberOfErrorCorrectionBlocks = getNumberOfErrorCorrectionBlocks(version, ecl);
	int numberOfErrorCorrectionCodewordsPerBlock = getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl);
	int numberOfShortBlocks = numberOfErrorCorrectionBlocks - totalNumberOfCodewords % numberOfErrorCorrectionBlocks;
	int lengthOfShortBlock = totalNumberOfCodewords / numberOfErrorCorrectionBlocks;
	char coef[32];
	int coef_length = numberOfErrorCorrectionCodewordsPerBlock;
	memset(coef, 0, coef_length);
	coef[coef_length - 1] = 1;
	for (int j, root = 1, i = 0; i < coef_length; i++) {
		for (j = 0; j < coef_length - 1; j++)
			coef[j] = (char)(gf_mul(coef[j] & 0xff, root) ^ coef[j + 1]);
		coef[j] = (char)gf_mul(coef[j] & 0xff, root);
		root = gf_mul(root, 2);
	}
	int errorCorrectionBase = lengthOfShortBlock + 1 - coef_length;
	char blocks[5120];
	memset(blocks, 0, sizeof(blocks));
	for (int pos = 0, i = 0; i < numberOfErrorCorrectionBlocks; i++) {
		char* block = blocks + i * (lengthOfShortBlock + 1);
		int len = lengthOfShortBlock + (i < numberOfShortBlocks ? 0 : 1) - coef_length;
		for (int j = 0, k; j < len; j++) {
			int factor = ((block[j] = codewords[pos + j]) ^ block[errorCorrectionBase]) & 0xff;
			for (k = 0; k < coef_length - 1; k++)
				block[errorCorrectionBase + k] = gf_mul(coef[k] & 0xff, factor) ^ block[errorCorrectionBase + k + 1];
			block[errorCorrectionBase + k] = gf_mul(coef[k] & 0xff, factor);
		}
		pos += len;
	}
	for (int pos = 0, i = 0; i <= lengthOfShortBlock; i++)
		for (int j = 0; j < numberOfErrorCorrectionBlocks; j++)
			if (i != lengthOfShortBlock - numberOfErrorCorrectionCodewordsPerBlock || j >= numberOfShortBlocks)
				r[pos++] = blocks[j * (lengthOfShortBlock + 1) + i];
	return totalNumberOfCodewords;
}

static void* extractCodewords(char* modules, char* funmask, int size, int version, int ecl, int* length) {
	int totalNumberOfCodewords = getTotalNumberOfCodewords(version);
	int numberOfErrorCorrectionBlocks = getNumberOfErrorCorrectionBlocks(version, ecl);
	int numberOfErrorCorrectionCodewordsPerBlock = getNumberOfErrorCorrectionCodewordsPerBlock(version, ecl);
	int numberOfShortBlocks = numberOfErrorCorrectionBlocks - totalNumberOfCodewords % numberOfErrorCorrectionBlocks;
	int lengthOfShortBlock = totalNumberOfCodewords / numberOfErrorCorrectionBlocks;
	char blocks[5120];
	char codewords[3706];
	readErrorCorrectionCodewords(modules, funmask, size, codewords, length);
	for (int pos = 0, i = 0; i <= lengthOfShortBlock; i++)
		for (int j = 0; j < numberOfErrorCorrectionBlocks; j++)
			if (i != lengthOfShortBlock - numberOfErrorCorrectionCodewordsPerBlock || j >= numberOfShortBlocks)
				blocks[j * (lengthOfShortBlock + 1) + i] = codewords[pos++];
	char* r = (char*)malloc(*length = totalNumberOfCodewords - numberOfErrorCorrectionCodewordsPerBlock * numberOfErrorCorrectionBlocks);
	int workbuffer[30 * 4 + 3];
	for (int n = 0, i = 0; i < numberOfErrorCorrectionBlocks; i++) {
		char* block = blocks + i * (lengthOfShortBlock + 1);
		int len;
		if (i < numberOfShortBlocks) {
			len = lengthOfShortBlock;
			for (int j = len - numberOfErrorCorrectionCodewordsPerBlock; j < len; j++)
				block[j] = block[j + 1];
		}
		else {
			len = lengthOfShortBlock + 1;
		}
		if (!rs_correct_msg(block, len, numberOfErrorCorrectionCodewordsPerBlock, workbuffer)){
			free(r);
			return NULL;
		}
		len -= numberOfErrorCorrectionCodewordsPerBlock;
		for (int j = 0; j < len; j++)
			r[n++] = block[j];
	}
	return r;
}

static void releaseQrCode(QrCode qrcode) {
	free(qrcode->modules);
	free(qrcode);
}

#define S0 "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 "
#define S1 "\" stroke=\"none\">\n\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n\t<path d=\""
#define S2 " fill=\"#000000\"/>\n</svg>\n"
static char* toSvgXML(QrCode qrcode) {
	char buffer[16];
	char *buffer_end = buffer + sizeof(buffer);
	ptrdiff_t length = 0, capacity = 1024;
	char *text = (char *)malloc(capacity);
	memcpy(text + length, S0, sizeof(S0) - 1); length += sizeof(S0) - 1;
	char *p = buffer_end;
	for (int v = qrcode->size + 8; v > 0; v /= 10)
		*--p = v % 10 + '0';
	memcpy(text + length, p, buffer_end - p); length += buffer_end - p;
	text[length++] = ' ';
	memcpy(text + length, p, buffer_end - p); length += buffer_end - p;
	memcpy(text + length, S1, sizeof(S1) - 1); length += sizeof(S1) - 1;
	char *modules = (char *)qrcode->modules;
	int size = qrcode->size;
	for (int n = 0, y = 0; y < size; y++)
		for (int x = 0; x < size; x++, n++)
			if (modules[n]){
				char rec[32];
				ptrdiff_t pos = 1;
				rec[0] = 'M';
				p = buffer_end;
				for (int v = x + 4; v > 0; v /= 10)
					*--p = v % 10 + '0';
				memcpy(rec + pos, p, buffer_end - p); pos += buffer_end - p;
				rec[pos++] = ',';
				p = buffer_end;
				for (int v = y + 4; v > 0; v /= 10)
					*--p = v % 10 + '0';
				memcpy(rec + pos, p, buffer_end - p); pos += buffer_end - p;
				memcpy(rec + pos, "h1v1h-1z ", 9); pos += 9;
				if (length + pos > capacity)
					text = (char *)realloc(text, capacity <<= 1);
				memcpy(text + length, rec, pos); length += pos;
			}
	text[length - 1] = '"';
	if (length + (int)sizeof(S2) > capacity)
		text = (char *)realloc(text, length + sizeof(S2));
	memcpy(text + length, S2, sizeof(S2));
	return text;
}

static char *ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

QrCode qr_encode(void *_data, int len, int ecl) {
	char *data = (char *)_data;
	int i = 0, version, mode, nbits = 4, lbits = 0, pbits = -1, pbytes;
	for (; i < len && data[i] >= 48 && data[i] <= 57; i++)
		;
	if (i == len) {
		mode = 1;
		nbits += (len / 3) * 10;
		switch (len % 3) {
		case 2:
			nbits += 7;
			break;
		case 1:
			nbits += 4;
		}
	}
	else {
		for (; i < len && strchr(ALPHANUMERIC, data[i]) != NULL; i++)
			;
		if (i == len) {
			mode = 2;
			nbits += (len / 2) * 11 + (len & 1) * 6;
		}
		else {
			mode = 4;
			nbits += len * 8;
		}
		mode = i == len ? 2 : 4;
	}
	for (version = 0; pbits < 0 && ++version <= 40; pbits = getBitCapacity(version, ecl) - nbits - lbits) {
		if (version < 10)
			lbits = mode == 1 ? 10 : mode == 2 ? 9 : 8;
		else if (version < 27)
			lbits = mode == 1 ? 12 : mode == 2 ? 11 : 16;
		else
			lbits = mode == 1 ? 14 : mode == 2 ? 13 : 16;
	}
	if (pbits < 0)
		return NULL;
	struct bit_stream *bs = bit_stream_alloc();
	bit_stream_append(bs, mode, 4);
	bit_stream_append(bs, len, lbits);
	switch (mode) {
	case 1:
		for (i = 0; i <= len - 3; i += 3)
			bit_stream_append(bs, (data[i] - 48) * 100 + (data[i + 1] - 48) * 10 + (data[i + 2] - 48), 10);
		switch (len - i) {
		case 2:
			bit_stream_append(bs, (data[i] - 48) * 10 + (data[i + 1] - 48), 7);
			break;
		case 1:
			bit_stream_append(bs, (data[i] - 48), 4);
			break;
		}
		break;
	case 2:
		for (i = 0; i <= len - 2; i += 2)
			bit_stream_append(bs, (unsigned int)((strchr(ALPHANUMERIC, data[i]) - ALPHANUMERIC) * 45 + (strchr(ALPHANUMERIC, data[i + 1]) - ALPHANUMERIC)), 11);
		if (i < len)
			bit_stream_append(bs, (unsigned int)(strchr(ALPHANUMERIC, data[i]) - ALPHANUMERIC), 6);
		break;
	default:
		for (i = 0; i < len; i++)
			bit_stream_append(bs, data[i] & 0xff, 8);
	}
	if (pbits >= 4) {
		bit_stream_append(bs, 0, 4);
		pbits -= 4;
	}
	pbytes = pbits >> 3;
	pbits &= 7;
	if (pbits != 0)
		bit_stream_append(bs, 0, 8 - pbits);
	for (; pbytes >= 2; pbytes -= 2)
		bit_stream_append(bs, 0xec11, 16);
	if (pbytes > 0)
		bit_stream_append(bs, 0xec, 8);
	int size = version * 4 + 17;
	int area = size * size;
	char *modules = (char *)calloc(1, area);
	char *funmask = (char *)calloc(1, area);
	initializeVersion(version, modules, funmask, size);
	char ecCodewords[4096];
	placeErrorCorrectionCodewords(modules, funmask, size, ecCodewords, generateErrorCorrectionCodewords(bs->data, bit_stream_bytelength(bs), version, ecl, ecCodewords));
	bit_stream_free(bs);
	int pattern = selectMaskPattern(modules, funmask, size, ecl);
	maskPattern(modules, funmask, size, pattern);
	placeMask(modules, funmask, size, ecl, pattern);
	free(funmask);
	QrCode qrcode = (struct tagQrCode *)malloc(sizeof(struct tagQrCode));
	qrcode->modules = modules;
	qrcode->size = size;
	qrcode->release = releaseQrCode;
	qrcode->toSvgXML = toSvgXML;
	return qrcode;
}

#define X 0
#define Y 1
#define W 2
#define H 3

#define TR 0
#define TL 1
#define BL 2

static int testFinderPattern(int w0, int w1, int w2, int w3, int w4) {
	float d, scale = (w0 + w1 + w2 + w3 + w4) / 14.0f;
	if (scale == 0)
		return 0;
	d = w0 / scale;
	if (d < 1 || d > 3)
		return 0;
	d = w1 / scale;
	if (d < 1 || d > 3)
		return 0;
	d = w2 / scale;
	if (d < 5 || d > 7)
		return 0;
	d = w3 / scale;
	if (d < 1 || d > 3)
		return 0;
	d = w4 / scale;
	if (d < 1 || d > 3)
		return 0;
	return 1;
}

static void swapFinder(float finder[3][4], int a, int b) {
	for (int i = 0; i < 4; i++) {
		float tmp = finder[a][i];
		finder[a][i] = finder[b][i];
		finder[b][i] = tmp;
	}
}

static int scanFinderYCenter(char* data, int width, int height, int step, int xc, int yc, int c) {
	int y0, y1, y2, y3, y4, y5;
	for (y2 = yc - step; y2 >= 0 && data[y2 * width + xc] == c; y2 -= step)
		;
	for (y1 = y2 - step; y1 >= 0 && data[y1 * width + xc] != c; y1 -= step)
		;
	for (y0 = y1 - step; y0 >= 0 && data[y0 * width + xc] == c; y0 -= step)
		;
	for (y3 = yc + step; y3 < height && data[y3 * width + xc] == c; y3 += step)
		;
	for (y4 = y3 + step; y4 < height && data[y4 * width + xc] != c; y4 += step)
		;
	for (y5 = y4 + step; y5 < height && data[y5 * width + xc] == c; y5 += step)
		;
	return testFinderPattern(y1 - y0, y2 - y1, y3 - y2 - step, y4 - y3, y5 - y4) ? (y2 + y3) / step / 2 * step : -1;
}

static int scanFinderXCenter(char* data, int width, int height, int step, int xc, int yc, int c) {
	int x0, x1, x2, x3, x4, x5;
	for (x2 = xc - step; x2 >= 0 && data[yc * width + x2] == c; x2 -= step)
		;
	for (x1 = x2 - step; x1 >= 0 && data[yc * width + x1] != c; x1 -= step)
		;
	for (x0 = x1 - step; x0 >= 0 && data[yc * width + x0] == c; x0 -= step)
		;
	for (x3 = xc + step; x3 < width && data[yc * width + x3] == c; x3 += step)
		;
	for (x4 = x3 + step; x4 < width && data[yc * width + x4] != c; x4 += step)
		;
	for (x5 = x4 + step; x5 < width && data[yc * width + x5] == c; x5 += step)
		;
	return testFinderPattern(x1 - x0, x2 - x1, x3 - x2 - step, x4 - x3, x5 - x4) ? (x2 + x3) / step / 2 * step : -1;
}

static int scanFinder(char* data, int width, int height, int step, float finder[3][4]) {
	int64_t metrics[] = { 0, 0, 0, 0, 0 };
	int modifies_capacity = 1024, *modifies = (int *)malloc(modifies_capacity * sizeof(int)), mcount = 0;
	for (int y = 0; y < height; y += step) {
		for (int x = 0, w0 = 0, w1 = 0, w2 = 0, w3 = 0, w4, c; x < width; w0 = w1, w1 = w2, w2 = w3, w3 = w4) {
			for (w4 = -x, c = data[y * width + x]; (x += step) < width && data[y * width + x] == c;)
				;
			if (testFinderPattern(w0, w1, w2, w3, w4 += x)) {
				int x3 = x - w4 - w3, x2 = x3 - w2;
				int xc = (x2 + x3) / step / 2 * step;
				int yc = y;
				yc = scanFinderYCenter(data, width, height, step, xc, yc, c);
				if (yc == -1)
					continue;
				xc = scanFinderXCenter(data, width, height, step, xc, yc, c);
				if (xc == -1)
					continue;
				yc = scanFinderYCenter(data, width, height, step, xc, yc, c);
				if (yc == -1)
					continue;
				int w = 0;
				for (int i = step; w == 0; i += step) {
					for (int j = yc - i; j <= yc + i && w == 0; j += step)
						for (int k = xc - i; k <= xc + i; k += step)
							if (j < 0 || j >= height || k < 0 || k >= height || data[j * width + k] != c) {
								w = i - step;
								break;
							}
				}
				int t, b, l, r;
				for (t = yc - step; t >= yc - w && scanFinderXCenter(data, width, height, step, xc, t, c) != -1; t -= step)
					;
				for (b = yc + step; b <= yc + w && scanFinderXCenter(data, width, height, step, xc, b, c) != -1; b += step)
					;
				for (l = xc - step; l >= xc - w && scanFinderYCenter(data, width, height, step, l, yc, c) != -1; l -= step)
					;
				for (r = xc + step; r <= xc + w && scanFinderYCenter(data, width, height, step, r, yc, c) != -1; r += step)
					;
				int64_t metric = (((int64_t)(b - t) * (r - l)) << 33) | (c == 0 ? 0x100000000LL : 0) | ((yc & 0xffff) << 16) | (xc & 0xffff);
				for (int i = 0; i < 5; i++) {
					if (metric > metrics[i]) {
						for (int k = 4; k > i; k--)
							metrics[k] = metrics[k - 1];
						metrics[i] = metric;
						break;
					}
				}
				for (int i = yc - w; i <= yc + w; i += step) {
					int pos = i * width + xc;
					data[pos] = ~data[pos];
					modifies[mcount++] = pos;
					if (mcount == modifies_capacity)
						modifies = (int *)realloc(modifies, (modifies_capacity <<= 1) * sizeof(int));
				}
				for (int i = xc - w; i <= xc + w; i += step) {
					int pos = yc * width + i;
					data[pos] = ~data[pos];
					modifies[mcount++] = pos;
					if (mcount == modifies_capacity)
						modifies = (int *)realloc(modifies, (modifies_capacity <<= 1) * sizeof(int));
				}
			}
		}
	}
	for (int i = 0; i < mcount; i++)
		data[modifies[i]] = ~data[modifies[i]];
	free(modifies);
	int group0[3], group1[3], *group, c0 = 0, c1 = 0;
	for (int i = 0; i < 5 && metrics[i] != 0 && c0 < 3 && c1 < 3; i++)
		if ((metrics[i] & 0x100000000LL) != 0)
			group0[c0++] = i;
		else
			group1[c1++] = i;
	if (c0 == 3)
		group = group0;
	else if (c1 == 3)
		group = group1;
	else
		return 0;
	for (int i = 0; i < 3; i++) {
		int64_t v = metrics[group[i]];
		finder[i][X] = (float)(v & 0xffff);
		finder[i][Y] = (float)((v >> 16) & 0xffff);
	}
	float distance[3][3];
	for (int i = 0; i < 2; i++)
		for (int j = i + 1; j < 3; j++) {
			float dx = finder[i][X] - finder[j][X];
			float dy = finder[i][Y] - finder[j][Y];
			distance[i][j] = dx * dx + dy * dy;
		}
	if (distance[0][1] > distance[0][2] && distance[0][1] > distance[1][2])
		swapFinder(finder, 2, 1);
	else if (distance[1][2] > distance[0][1] && distance[1][2] > distance[0][2])
		swapFinder(finder, 0, 1);
	if ((finder[0][X] - finder[1][X]) * (finder[2][Y] - finder[1][Y]) < (finder[2][X] - finder[1][X]) * (finder[0][Y] - finder[1][Y]))
		swapFinder(finder, 0, 2);
	return c0 == 3 ? 1 : -1;
}

static int scanFinderEdgePixel(float *p0, float *p1, float *p2, char* data, int reverse, int width, int height, int *r) {
	int x = (int)p2[X];
	int y = (int)p2[Y];
	int dx = (int)(p1[X] - p0[X]);
	int dy = (int)(p1[Y] - p0[Y]);
	int xdir, ydir;
	if (dx < 0) {
		xdir = -1;
		dx = -dx;
	}
	else {
		xdir = 1;
	}
	if (dy < 0) {
		ydir = -1;
		dy = -dy;
	}
	else {
		ydir = 1;
	}
	int t;
	if (dx > dy) {
		t = 0;
	}
	else {
		t = dx;
		dx = dy;
		dy = t;
		t = x;
		x = y;
		y = t;
		t = xdir;
		xdir = ydir;
		ydir = t;
		t = width;
		width = height;
		height = t;
		t = 1;
	}
	int e = -dx;
	int status = 0;
	for (dx <<= 1, dy <<= 1; x >= 0 && x < width && y >= 0 && y < height; x += xdir) {
		int dark = (t == 0 ? data[y * width + x] == 0 : data[x * height + y] == 0) ^ reverse;
		switch (status) {
		case 0:
			if (!dark)
				status = 1;
			break;
		case 1:
			if (dark)
				status = 2;
			break;
		case 2:
			if (!dark)
				status = 3;
			break;
		case 3:
			if (t == 0) {
				r[X] = x;
				r[Y] = y;
			}
			else {
				r[X] = y;
				r[Y] = x;
			}
			return 1;
		}
		e += dy;
		if (e > 0) {
			y += ydir;
			e -= dx;
		}
	}
	return 0;
}

static int countDarkPixel(int x0, int y0, int x1, int y1, char* data, int reverse, int width, int threshold) {
	int x, y, t, dir;
	int dx = abs(x1 - x0);
	int dy = abs(y1 - y0);
	if (dy > dx) {
		t = dx;
		dx = dy;
		dy = t;
		t = x0;
		x0 = y0;
		y0 = t;
		t = x1;
		x1 = y1;
		y1 = t;
		t = 1;
	}
	else
		t = 0;
	int e = -dx;
	if (x0 < x1) {
		x = x0;
		y = y0;
		dir = y1 > y0 ? 1 : -1;
	}
	else {
		x = x1;
		y = y1;
		x1 = x0;
		dir = y0 > y1 ? 1 : -1;
	}
	int c = 0;
	for (dx <<= 1, dy <<= 1; x <= x1; x++) {
		if (t == 0) {
			if ((data[y * width + x] == 0) ^ reverse)
				if (++c >= threshold)
					break;
		}
		else {
			if ((data[x * width + y] == 0) ^ reverse)
				if (++c >= threshold)
					break;
		}
		e += dy;
		if (e > 0) {
			y += dir;
			e -= dx;
		}
	}
	return c;
}

static int scanEdgePixel(float *p0, float *p1, float *p2, int *p3, char *data, int reverse, int width, int height, int threshold, int *r) {
	int x = (int)p2[X];
	int y = (int)p2[Y];
	int dx = (int)(p1[X] - p0[X]);
	int dy = (int)(p1[Y] - p0[Y]);
	int xdir, ydir;
	if (dx < 0) {
		xdir = -1;
		dx = -dx;
	}
	else {
		xdir = 1;
	}
	if (dy < 0) {
		ydir = -1;
		dy = -dy;
	}
	else {
		ydir = 1;
	}
	int t;
	if (dx > dy) {
		t = 0;
	}
	else {
		t = dx;
		dx = dy;
		dy = t;
		t = x;
		x = y;
		y = t;
		t = xdir;
		xdir = ydir;
		ydir = t;
		t = width;
		width = height;
		height = t;
		t = 1;
	}
	int e = -dx;
	int c0 = 0;
	int c1 = 0;
	int c2 = INT32_MAX >> 1;
	for (dx <<= 1, dy <<= 1; x >= 0 && x < width && y >= 0 && y < height; x += xdir) {
		int count = t == 0 ? countDarkPixel((int)p3[X], (int)p3[Y], x, y, data, reverse, width, threshold)
			: countDarkPixel((int)p3[X], (int)p3[Y], y, x, data, reverse, height, threshold);
		c0 = c1;
		c1 = c2;
		c2 = count;
		if (c0 < threshold && c1 < threshold && c2 < threshold) {
			if (t == 0) {
				r[X] = x;
				r[Y] = y;
			}
			else {
				r[X] = y;
				r[Y] = x;
			}
			return 1;
		}
		e += dy;
		if (e > 0) {
			y += ydir;
			e -= dx;
		}
	}
	return 0;
}

static void cross(int *p0, int *p1, int *p2, int *p3, float *rp) {
	if (p0[X] == p1[X]) {
		float k1 = ((float)p2[Y] - p3[Y]) / (p2[X] - p3[X]);
		float c1 = p2[Y] - k1 * p2[X];
		rp[X] = (float)p1[X];
		rp[Y] = k1 * p1[X] + c1;
	}
	else if (p2[X] == p3[X]) {
		float k0 = ((float)p0[Y] - p1[Y]) / (p0[X] - p1[X]);
		float c0 = p0[Y] - k0 * p0[X];
		rp[X] = (float)p2[X];
		rp[Y] = k0 * p2[X] + c0;
	}
	else {
		float k0 = ((float)p0[Y] - p1[Y]) / (p0[X] - p1[X]);
		float c0 = p0[Y] - k0 * p0[X];
		float k1 = ((float)p2[Y] - p3[Y]) / (p2[X] - p3[X]);
		float c1 = p2[Y] - k1 * p2[X];
		float x = (c1 - c0) / (k0 - k1);
		rp[X] = x;
		rp[Y] = k0 * x + c0;
	}
}

static int scanBox(char  *data, int reverse, int width, int height, float finder[3][4], float box[4][2]) {
	int tll[2], bll[2], tlt[2], trt[2], blb[2], trr[2], bmd[2], rmd[2];
	if (!scanFinderEdgePixel(finder[TR], finder[TL], finder[TL], data, reverse, width, height, tll))
		return 0;
	if (!scanFinderEdgePixel(finder[TR], finder[TL], finder[BL], data, reverse, width, height, bll))
		return 0;
	if (!scanFinderEdgePixel(finder[BL], finder[TL], finder[TL], data, reverse, width, height, tlt))
		return 0;
	if (!scanFinderEdgePixel(finder[BL], finder[TL], finder[TR], data, reverse, width, height, trt))
		return 0;
	if (!scanFinderEdgePixel(finder[TL], finder[BL], finder[BL], data, reverse, width, height, blb))
		return 0;
	if (!scanFinderEdgePixel(finder[TL], finder[TR], finder[TR], data, reverse, width, height, trr))
		return 0;
	float center[] = { (finder[TR][X] + finder[BL][X]) / 2, (finder[TR][Y] + finder[BL][Y]) / 2 };
	float dx = finder[BL][X] - bll[X];
	float dy = finder[BL][Y] - bll[Y];
	int threshold = (int)(sqrtf(dx * dx + dy * dy) / 3.5f);
	if (!scanEdgePixel(finder[TL], finder[BL], center, blb, data, reverse, width, height, threshold, bmd))
		return 0;
	dx = finder[TR][X] - trt[X];
	dy = finder[TR][Y] - trt[Y];
	threshold = (int)(sqrtf(dx * dx + dy * dy) / 3.5f);
	if (!scanEdgePixel(finder[TL], finder[TR], center, trr, data, reverse, width, height, threshold, rmd))
		return 0;
	cross(tll, bll, tlt, trt, box[0]);
	if (box[0][X] < 0 || box[0][X] >= width || box[0][Y] < 0 || box[0][Y] >= height)
		return 0;
	cross(rmd, trr, tlt, trt, box[1]);
	if (box[1][X] < 0 || box[1][X] >= width || box[1][Y] < 0 || box[1][Y] >= height)
		return 0;
	cross(tll, bll, blb, bmd, box[2]);
	if (box[2][X] < 0 || box[2][X] >= width || box[2][Y] < 0 || box[2][Y] >= height)
		return 0;
	cross(blb, bmd, rmd, trr, box[3]);
	if (box[3][X] < 0 || box[3][X] >= width || box[3][Y] < 0 || box[3][Y] >= height)
		return 0;
	return 1;
}

struct view_port {
	char *data;
	int reverse;
	int width;
	int height;
	float size;
	float coef[9];
	int mirror;
};

#undef max
#undef min

static float max(float a, float b) {
	return a > b ? a : b;
}

static float min(float a, float b) {
	return a < b ? a : b;
}

static void view_port_free(struct view_port *vp) {
	free(vp);
}

static int adjustFinder(struct view_port *vp, float *finder);
static struct view_port *view_port_alloc(char* data, int reverse, int width, int height, float box[4][2], float finder[3][4]) {
	float maxx = max(max(box[0][X], box[1][X]), max(box[2][X], box[3][X]));
	float minx = min(min(box[0][X], box[1][X]), min(box[2][X], box[3][X]));
	float maxy = max(max(box[0][Y], box[1][Y]), max(box[2][Y], box[3][Y]));
	float miny = min(min(box[0][Y], box[1][Y]), min(box[2][Y], box[3][Y]));
	float size = max(maxx - minx, maxy - miny);
	float coef[9];
	quadrilateralToQuadrilateral(box[0][X], box[0][Y], box[1][X], box[1][Y], box[2][X], box[2][Y], box[3][X], box[3][Y], 0, 0, size, 0, 0, size, size, size, coef);
	transform(coef, finder[TL], 2);
	if (finder[TL][X] <= 0 || finder[TL][X] >= size || finder[TL][Y] <= 0 || finder[TL][Y] >= size)
		return NULL;
	transform(coef, finder[TR], 2);
	if (finder[TR][X] <= 0 || finder[TR][X] >= size || finder[TR][Y] <= 0 || finder[TR][Y] >= size)
		return NULL;
	transform(coef, finder[BL], 2);
	if (finder[BL][X] <= 0 || finder[BL][X] >= size || finder[BL][Y] <= 0 || finder[BL][Y] >= size)
		return NULL;
	struct view_port *vp = (struct view_port *)malloc(sizeof(struct view_port));
	vp->data = data;
	vp->reverse = reverse;
	vp->width = width;
	vp->height = height;
	vp->size = size;
	vp->mirror = 0;
	quadrilateralToQuadrilateral(0, 0, size, 0, 0, size, size, size, box[0][X], box[0][Y], box[1][X], box[1][Y], box[2][X], box[2][Y], box[3][X], box[3][Y], vp->coef);
	if (!adjustFinder(vp, finder[TL]) || !adjustFinder(vp, finder[TR]) || !adjustFinder(vp, finder[BL])) {
		view_port_free(vp);
		return NULL;
	}
	return vp;
}

static int view_port_test(struct view_port *vp, float x, float y) {
	float point[2];
	if (vp->mirror) {
		point[0] = y;
		point[1] = x;
	}
	else {
		point[0] = x;
		point[1] = y;
	}
	transform(vp->coef, point, 2);
	int tx = (int)point[X];
	int ty = (int)point[Y];
	return tx < 0 || tx >= vp->width || ty < 0 || ty >= vp->height ? 0 : (vp->data[ty * vp->width + tx] == 0) ^ vp->reverse;
}

static int adjustFinder(struct view_port *vp, float *finder) {
	float x = finder[X];
	float y = finder[Y];
	float size = vp->size;
	int w0 = 0, w1 = 0, w2 = 0, w3 = 0, w4 = 0;
	for (; x >= 0 && view_port_test(vp, x, y); x--)
		w2++;
	for (; x >= 0 && !view_port_test(vp, x, y); x--)
		w1++;
	for (; x >= 0 && view_port_test(vp, x, y); x--)
		w0++;
	float xl = x;
	x = finder[X];
	for (; x < size && view_port_test(vp, x, y); x++)
		w2++;
	for (; x < size && !view_port_test(vp, x, y); x++)
		w3++;
	for (; x < size && view_port_test(vp, x, y); x++)
		w4++;
	if (!testFinderPattern(w0, w1, w2 - 1, w3, w4))
		return 0;
	float xr = x;
	x = finder[X];
	w0 = w1 = w2 = w3 = w4 = 0;
	for (; y >= 0 && view_port_test(vp, x, y); y--)
		w2++;
	for (; y >= 0 && !view_port_test(vp, x, y); y--)
		w1++;
	for (; y >= 0 && view_port_test(vp, x, y); y--)
		w0++;
	float yt = y;
	y = finder[Y];
	for (; y < size && view_port_test(vp, x, y); y++)
		w2++;
	for (; y < size && !view_port_test(vp, x, y); y++)
		w3++;
	for (; y < size && view_port_test(vp, x, y); y++)
		w4++;
	if (!testFinderPattern(w0, w1, w2 - 1, w3, w4))
		return 0;
	float yb = y;
	finder[X] = (xl + xr) / 2;
	finder[Y] = (yt + yb) / 2;
	finder[H] = (xr - xl - 1);
	finder[W] = (yb - yt - 1);
	return 1;
}

static char sample(struct view_port *vp, float x, float y) {
	int dark = 0;
	for (int i = -1; i <= 1; i++)
		for (int j = -1; j <= 1; j++)
			if (view_port_test(vp, x + j, y + i))
				dark++;
	return dark > 4;
}

static int ECC_VERSION[] = { 0x7c94, 0x85bc, 0x9a99, 0xa4d3, 0xbbf6, 0xc762, 0xd847, 0xe60d, 0xf928, 0x10b78, 0x1145d, 0x12a17,
0x13532, 0x149a6, 0x15683, 0x168c9, 0x177ec, 0x18ec4, 0x191e1, 0x1afab, 0x1b08e, 0x1cc1a, 0x1d33f, 0x1ed75, 0x1f250, 0x209d5,
0x216f0, 0x228ba, 0x2379f, 0x24b0b, 0x2542e, 0x26a64, 0x27541, 0x28c69, };

static int bitCount(int value) {
	unsigned int i = (unsigned int)value;
	i = i - ((i >> 1) & 0x55555555);
	i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
	i = (i + (i >> 4)) & 0x0f0f0f0f;
	i = i + (i >> 8);
	i = i + (i >> 16);
	return i & 0x3f;
}

static int decodeVersion(int encodedVersion) {
	for (int i = 0; i < (int)(sizeof(ECC_VERSION) / sizeof(ECC_VERSION[0])); i++)
		if (bitCount(ECC_VERSION[i] ^ encodedVersion) <= 3)
			return i + 7;
	return -1;
}

static int scanVersion(struct view_port *vp, float finder[3][4]) {
	float scale = (finder[TL][W] + finder[TR][W]) / 14.0f;
	int version = (int)round(((finder[TR][X] - finder[TL][X]) / scale - 10) / 4);
	if (version < 7)
		return version;
	scale = finder[TR][W] / 7.0f;
	float bx = finder[TR][X] - scale * 7;
	float by = finder[TR][Y] - scale * 3;
	version = 0;
	for (int i = 0; i < 6; i++, by += scale)
		for (int j = 0; j < 3; j++)
			if (sample(vp, bx + scale * j, by))
				version |= (1 << (i * 3 + j));
	if ((version = decodeVersion(version)) != -1)
		return version;
	scale = finder[BL][H] / 7.0f;
	bx = finder[BL][X] - scale * 3;
	by = finder[BL][Y] - scale * 7;
	version = 0;
	for (int i = 0; i < 6; i++, bx += scale)
		for (int j = 0; j < 3; j++)
			if (sample(vp, bx, by + scale * j))
				version |= (1 << (i * 3 + j));
	return decodeVersion(version);
}

static int ECC_FORMAT[] = { 0x5412, 0x5125, 0x5e7c, 0x5b4b, 0x45f9, 0x40ce, 0x4f97, 0x4aa0,
0x77f4, 0x72f3, 0x7daa, 0x789d, 0x662f, 0x6318, 0x6c41, 0x6976, 0x1689, 0x13be, 0x1ce7, 0x19d0, 0x0762,
0x0255, 0x0d0c, 0x083b, 0x355f, 0x3068, 0x3f31, 0x3a06, 0x24b4, 0x2183, 0x2eda, 0x2bed };

static int decodeFormat(int encodedFormat) {
	for (int i = 0; i < (int)(sizeof(ECC_FORMAT) / sizeof(ECC_FORMAT[0])); i++)
		if (bitCount(ECC_FORMAT[i] ^ encodedFormat) <= 3)
			return i;
	return -1;
}

static int scanFormat(struct view_port *vp, float finder[3][4]) {
	float scale = finder[TL][W] / 7.0f;
	float bx = finder[TL][X] + scale * 5;
	float by = finder[TL][Y] + scale * 5;
	int format = 0;
	if (sample(vp, bx, by))
		format |= 1 << 7;
	if (sample(vp, bx, by - scale))
		format |= 1 << 6;
	if (sample(vp, bx - scale, by))
		format |= 1 << 8;
	for (int i = 3; i < 9; i++) {
		if (sample(vp, bx, by - scale * i))
			format |= 1 << (8 - i);
		if (sample(vp, bx - scale * i, by))
			format |= 1 << (6 + i);
	}
	if ((format = decodeFormat(format)) != -1)
		return format;
	scale = finder[TR][H] / 7.0f;
	bx = finder[TR][X] + scale * 3;
	by = finder[TR][Y] + scale * 5;
	format = 0;
	for (int i = 0; i < 8; i++, bx -= scale)
		if (sample(vp, bx, by))
			format |= 1 << i;
	scale = finder[BL][W] / 7.0f;
	bx = finder[BL][X] + scale * 5;
	by = finder[BL][Y] - scale * 3;
	for (int i = 0; i < 7; i++, by += scale)
		if (sample(vp, bx, by))
			format |= 1 << (8 + i);
	return decodeFormat(format);
}

static int testAlignmentPattern(struct view_port *vp, float x, float y, float e, float res[2]) {
	float scale = e * 2;
	if (!view_port_test(vp, x, y) || x <= scale || x >= vp->size - scale || y <= scale || y >= vp->size - scale)
		return 0;
	int c;
	float l = x - 1;
	for (c = (int)scale; view_port_test(vp, l, y); l--)
		if (--c == 0)
			return 0;
	for (c = (int)scale; !view_port_test(vp, l, y); l--)
		if (--c == 0)
			return 0;
	float r = x + 1;
	for (c = (int)scale; view_port_test(vp, r, y); r++)
		if (--c == 0)
			return 0;
	for (c = (int)scale; !view_port_test(vp, r, y); r++)
		if (--c == 0)
			return 0;
	float t = y - 1;
	for (c = (int)scale; view_port_test(vp, x, t); t--)
		if (--c == 0)
			return 0;
	for (c = (int)scale; !view_port_test(vp, x, t); t--)
		if (--c == 0)
			return 0;
	float b = y + 1;
	for (c = (int)scale; view_port_test(vp, x, b); b++)
		if (--c == 0)
			return 0;
	for (c = (int)scale; !view_port_test(vp, x, b); b++)
		if (--c == 0)
			return 0;
	float w = r - l - 1;
	float h = b - t - 1;
	if (w * 2 > min(r - x, x - l) * 5)
		return 0;
	if (h * 2 > min(b - y, y - t) * 5)
		return 0;
	float el = e * 2;
	float eh = e * 4;
	if (w < el || w > eh || h < el || h > eh)
		return 0;
	res[X] = (l + r) / 2;
	res[Y] = (t + b) / 2;
	return 1;
}

static int scanAlignmentPattern(struct view_port *vp, float x, float y, float e, float *errata, float r[2]) {
	x += errata[X];
	y += errata[Y];
	if (testAlignmentPattern(vp, x, y, e, r))
		return 1;
	for (int k = 1; k <= e; k++)
		for (int l = 1; l <= e; l++) {
			if (testAlignmentPattern(vp, x + k, y + l, e, r)) {
				errata[X] += k;
				errata[Y] += l;
				return 1;
			}
			if (testAlignmentPattern(vp, x + k, y - l, e, r)) {
				errata[X] += k;
				errata[Y] += -l;
				return 1;
			}
			if (testAlignmentPattern(vp, x - k, y + l, e, r)) {
				errata[X] += -k;
				errata[Y] += l;
				return 1;
			}
			if (testAlignmentPattern(vp, x - k, y - l, e, r)) {
				errata[X] += -k;
				errata[Y] += -l;
				return 1;
			}
		}
	return 0;
}

static int scanAlignments(struct view_port *vp, float finder[3][4], int *positions, int positions_length, float alignments[7][7][2]) {
	float scale = finder[TL][W] / 7.0f;
	alignments[0][0][X] = finder[TL][X] + scale * 3;
	alignments[0][0][Y] = finder[TL][Y] + scale * 3;
	scale = finder[TR][W] / 7.0f;
	alignments[0][positions_length - 1][X] = finder[TR][X] - scale * 3;
	alignments[0][positions_length - 1][Y] = finder[TR][Y] + scale * 3;
	float errata[] = { 0, 0 };
	for (int i = 1; i < positions_length - 1; i++) {
		float e = scale;
		float x = alignments[0][i - 1][X] + scale * (positions[i] - positions[i - 1]);
		float y = alignments[0][i - 1][Y];
		if (!scanAlignmentPattern(vp, x, y, e, errata, alignments[0][i]))
			return 0;
	}
	scale = finder[BL][H] / 7.0f;
	alignments[positions_length - 1][0][X] = finder[BL][X] + scale * 3;
	alignments[positions_length - 1][0][Y] = finder[BL][Y] - scale * 3;
	errata[0] = errata[1] = 0;
	for (int i = 1; i < positions_length - 1; i++) {
		float e = scale;
		float x = alignments[0][0][X];
		float y = alignments[i - 1][0][Y] + scale * (positions[i] - positions[i - 1]);
		if (!scanAlignmentPattern(vp, x, y, e, errata, alignments[i][0]))
			return 0;
	}
	errata[0] = errata[1] = 0;
	float _max = max(max(finder[TL][W], finder[TL][H]), max(finder[TR][W], finder[TR][H]));
	_max = max(_max, max(finder[BL][W], finder[BL][H])) / 7.0f;
	for (int i = 1; i < positions_length; i++) {
		for (int j = 1; j < positions_length; j++) {
			float e = _max;
			float x = alignments[i][j - 1][X] - alignments[i - 1][j - 1][X] + alignments[i - 1][j][X];
			float y = alignments[i][j - 1][Y] - alignments[i - 1][j - 1][Y] + alignments[i - 1][j][Y];
			if (!scanAlignmentPattern(vp, x, y, e, errata, alignments[i][j])) {
				if (i != positions_length / 2 || j != positions_length / 2)
					return 0;
				alignments[i][j][X] = x;
				alignments[i][j][Y] = y;
			}
		}
	}
	return 1;
}

static void scanDataV1(struct view_port *vp, float finder[3][4], char *modules, char *funmask, int size) {
	float scale = finder[TL][W] / 7.0f;
	float xl = finder[TL][X] + scale * 3;
	float yl = finder[TL][Y] + scale * 3;
	for (; view_port_test(vp, xl, yl); xl++)
		;
	scale = finder[TR][W] / 7.0f;
	float xr = finder[TR][X] - scale * 3;
	float yr = finder[TR][Y] + scale * 3;
	for (; view_port_test(vp, xr, yr); xr--)
		;
	float hscale = sqrtf((xr - xl) * (xr - xl) + (yr - yl) * (yr - yl)) / 7.0f;
	scale = finder[TL][H] / 7.0f;
	float xt = finder[TL][X] + scale * 3;
	float yt = finder[TL][Y] + scale * 3;
	for (; view_port_test(vp, xt, yt); yt++)
		;
	scale = finder[BL][H] / 7.0f;
	float xb = finder[BL][X] + scale * 3;
	float yb = finder[BL][Y] - scale * 3;
	for (; view_port_test(vp, xb, yb); yb--)
		;
	float vscale = sqrtf((xb - xt) * (xb - xt) + (yb - yt) * (yb - yt)) / 7.0f;
	float xbase = xl - hscale * 6.5f;
	float ybase = yt - vscale * 6.5f;
	for (int p = 0, i = 0; i < size; i++)
		for (int j = 0; j < size; j++, p++)
			if (!funmask[p])
				modules[p] = sample(vp, xbase + hscale * j, ybase + vscale * i);
}

static void scanData(struct view_port *vp, float finder[3][4], int *positions, int positions_length, float alignments[7][7][2], char *modules, char *funmask, int size) {
	for (int i = 0; i < positions_length - 1; i++) {
		for (int j = 0; j < positions_length - 1; j++) {
			int ys = positions[i];
			int ye = positions[i + 1];
			int xs = positions[j];
			int xe = positions[j + 1];
			int hcount = xe - xs;
			int vcount = ye - ys;
			float xbase = alignments[i][j][X];
			float ybase = alignments[i][j][Y];
			float dx = xbase - alignments[i][j + 1][X];
			float dy = ybase - alignments[i][j + 1][Y];
			float scale = sqrtf(dx * dx + dy * dy) / hcount;
			for (int k = 1; k <= vcount; k++)
				for (int l = 1; l <= hcount; l++) {
					int pos = (ys + k) * size + xs + l;
					if (!funmask[pos])
						modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
				}
		}
		{
			int ye = positions[0];
			int xs = positions[i];
			int xe = positions[i + 1];
			int hcount = xe - xs;
			int vcount = positions[0];
			float xbase = alignments[0][i][X];
			float ybase = alignments[0][i][Y];
			float dx = xbase - alignments[0][i + 1][X];
			float dy = ybase - alignments[0][i + 1][Y];
			float scale = sqrtf(dx * dx + dy * dy) / hcount;
			for (int k = 1; k <= vcount; k++)
				for (int l = 1; l <= hcount; l++) {
					int pos = (ye - k) * size + xs + l;
					if (!funmask[pos])
						modules[pos] = sample(vp, xbase + scale * l, ybase - scale * k);
				}
		}
		{
			int ys = positions[positions_length - 1];
			int xs = positions[i];
			int xe = positions[i + 1];
			int hcount = xe - xs;
			int vcount = positions[0];
			float xbase = alignments[positions_length - 1][i][X];
			float ybase = alignments[positions_length - 1][i][Y];
			float dx = xbase - alignments[positions_length - 1][i + 1][X];
			float dy = ybase - alignments[positions_length - 1][i + 1][Y];
			float scale = sqrtf(dx * dx + dy * dy) / hcount;
			for (int k = 1; k <= vcount; k++)
				for (int l = 1; l <= hcount; l++) {
					int pos = (ys + k) * size + xs + l;
					if (!funmask[pos])
						modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
				}
		}
		{
			int ys = positions[i];
			int ye = positions[i + 1];
			int xe = positions[0];
			int vcount = ye - ys;
			int hcount = positions[0];
			float xbase = alignments[i][0][X];
			float ybase = alignments[i][0][Y];
			float dx = xbase - alignments[i + 1][0][X];
			float dy = ybase - alignments[i + 1][0][Y];
			float scale = sqrtf(dx * dx + dy * dy) / vcount;
			for (int k = 1; k <= vcount; k++)
				for (int l = 1; l <= hcount; l++){
					int pos = (ys + k) * size + xe - l;
					if (!funmask[pos])
						modules[pos] = sample(vp, xbase - scale * l, ybase + scale * k);
				}
		}
		{
			int ys = positions[i];
			int ye = positions[i + 1];
			int xs = positions[positions_length - 1];
			int vcount = ye - ys;
			int hcount = positions[0];
			float xbase = alignments[i][positions_length - 1][X];
			float ybase = alignments[i][positions_length - 1][Y];
			float dx = xbase - alignments[i + 1][positions_length - 1][X];
			float dy = ybase - alignments[i + 1][positions_length - 1][Y];
			float scale = sqrtf(dx * dx + dy * dy) / vcount;
			for (int k = 1; k <= vcount; k++)
				for (int l = 1; l <= hcount; l++) {
					int pos = (ys + k) * size + xs + l;
					if (!funmask[pos])
						modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
				}
		}
	}
	int ys = positions[positions_length - 1];
	int xs = ys;
	int hcount = positions[0];
	int vcount = hcount;
	float xbase = alignments[positions_length - 1][positions_length - 1][X];
	float ybase = alignments[positions_length - 1][positions_length - 1][Y];
	float dx = xbase - alignments[positions_length - 1][positions_length - 2][X];
	float dy = ybase - alignments[positions_length - 1][positions_length - 2][Y];
	float scale = sqrtf(dx * dx + dy * dy)
		/ (positions[positions_length - 1] - positions[positions_length - 2]);
	for (int k = 1; k <= vcount; k++)
		for (int l = 1; l <= hcount; l++) {
			int pos = (ys + k) * size + xs + l;
			if (!funmask[pos])
				modules[pos] = sample(vp, xbase + scale * l, ybase + scale * k);
		}
}

static struct bit_stream* eciTerminate(struct bit_stream* src, int eci) {
	struct bit_stream* dst = bit_stream_alloc();
	bit_stream_append(dst, 'J', 8);
	bit_stream_append(dst, 'Q', 8);
	bit_stream_append(dst, '2', 8);
	bit_stream_append(dst, '\\', 8);
	bit_stream_append(dst, eci % 1000000 / 100000 + 48, 8);
	bit_stream_append(dst, eci % 100000 / 10000 + 48, 8);
	bit_stream_append(dst, eci % 10000 / 1000 + 48, 8);
	bit_stream_append(dst, eci % 1000 / 100 + 48, 8);
	bit_stream_append(dst, eci % 100 / 10 + 48, 8);
	bit_stream_append(dst, eci % 10 / 1 + 48, 8);
	int length = bit_stream_bytelength(src);
	char *p = (char *)src->data;
	for (int i = 0; i < length; i++) {
		char b = p[i];
		if (b == '\\')
			bit_stream_append(dst, '\\', 8);
		bit_stream_append(dst, b, 8);
	}
	bit_stream_free(src);
	return dst;
}

static struct bit_stream* fnc1Start(struct bit_stream *dst, long appid, int eciadd) {
	bit_stream_append(dst, 'J', 8);
	bit_stream_append(dst, 'Q', 8);
	if (appid > 0) {
		bit_stream_append(dst, '5' + eciadd, 8);
		if (appid < 100) {
			bit_stream_append(dst, appid / 10 + 48, 8);
			bit_stream_append(dst, appid % 10 + 48, 8);
		}
		else {
			bit_stream_append(dst, appid - 100, 8);
		}
	}
	else {
		bit_stream_append(dst, '3' + eciadd, 8);
	}
	return dst;
}

static void *decodeCodewords(void *codewords, int *length, int version) {
	struct bit_stream *src = bit_stream_from_bytearray(codewords, *length);
	struct bit_stream *dst = bit_stream_alloc();
	struct bit_stream *fnc1 = NULL;
	int eci = -1;
	while (1) {
		int nchar, mode = bit_stream_get(src, 4);
		switch (mode) {
		case 0:{
			if (eci != -1)
				dst = eciTerminate(dst, eci);
			if (fnc1 != NULL) {
				int gs = 0;
				int length = bit_stream_bytelength(dst);
				char *p = (char *)dst->data;
				for (int i = 0; i < length; i++) {
					char b = p[i];
					if (gs) {
						if (b != '%')
							bit_stream_append(fnc1, 0x1d, 8);
						bit_stream_append(fnc1, b, 8);
						gs = 0;
					}
					else {
						if (b == '%')
							gs = 1;
						else
							bit_stream_append(fnc1, b, 8);
					}
				}
				bit_stream_free(dst);
				dst = fnc1;
			}
			void *data = bit_stream_to_bytearray(dst, length);
			bit_stream_free(src);
			bit_stream_free(dst);
			return data;
		}
		case 1:
			nchar = bit_stream_get(src, version < 10 ? 10 : version < 27 ? 12 : 14);
			for (; nchar >= 3; nchar -= 3) {
				int v = bit_stream_get(src, 10);
				bit_stream_append(dst, v / 100 + 48, 8);
				bit_stream_append(dst, v % 100 / 10 + 48, 8);
				bit_stream_append(dst, v % 10 + 48, 8);
			}
			if (nchar == 2) {
				int v = bit_stream_get(src, 7);
				bit_stream_append(dst, v / 10 + 48, 8);
				bit_stream_append(dst, v % 10 + 48, 8);
			}
			else if (nchar == 1) {
				int v = bit_stream_get(src, 4);
				bit_stream_append(dst, v % 10 + 48, 8);
			}
			break;
		case 2:
			nchar = bit_stream_get(src, version < 10 ? 9 : version < 27 ? 11 : 13);
			for (; nchar >= 2; nchar -= 2) {
				int v = bit_stream_get(src, 11);
				bit_stream_append(dst, ALPHANUMERIC[v / 45], 8);
				bit_stream_append(dst, ALPHANUMERIC[v % 45], 8);
			}
			if (nchar == 1) {
				int v = bit_stream_get(src, 6);
				bit_stream_append(dst, ALPHANUMERIC[v % 45], 8);
			}
			break;
		case 4:
			nchar = bit_stream_get(src, version < 10 ? 8 : 16);
			for (int i = 0; i < nchar; i++)
				bit_stream_append(dst, bit_stream_get(src, 8), 8);
			break;
		case 5:
			if (fnc1 == NULL) {
				fnc1 = fnc1Start(dst, -1, eci == -1 ? 0 : 1);
				dst = bit_stream_alloc();
			}
			break;
		case 7:
			if (eci != -1)
				dst = eciTerminate(dst, eci);
			eci = bit_stream_get(src, 8);
			if ((eci & 0x80) == 0)
				;
			else if ((eci & 0xc0) == 0x80)
				eci = ((eci & 0x3f) << 8) | bit_stream_get(src, 8);
			else if ((eci & 0xe0) == 0xc0)
				eci = ((eci & 0x1f) << 16) | bit_stream_get(src, 16);
			else
				goto _exit;
			break;
		case 8:
			nchar = bit_stream_get(src, version < 10 ? 8 : version < 27 ? 10 : 12);
			for (int i = 0; i < nchar; i++) {
				int v = bit_stream_get(src, 13) << 1;
				int cp = (kanji[v] << 8) | kanji[v + 1];
				if (cp < 0x80) {
					bit_stream_append(dst, cp, 8);
				}
				else if (cp >= 0x80 && cp <= 0x7ff) {
					bit_stream_append(dst, ((cp >> 6) & 0x1f) | 0xc0, 8);
					bit_stream_append(dst, (cp & 0x3f) | 0x80, 8);
				}
				else {
					bit_stream_append(dst, ((cp >> 12) & 0x0f) | 0xe0, 8);
					bit_stream_append(dst, ((cp >> 6) & 0x3f) | 0x80, 8);
					bit_stream_append(dst, (cp & 0x3f) | 0x80, 8);
				}
			}
			break;
		case 9:
			if (fnc1 == NULL) {
				fnc1 = fnc1Start(dst, bit_stream_get(src, 8), eci == -1 ? 0 : 1);
				dst = bit_stream_alloc();
			}
			break;
		default:
			goto _exit;
		}
	}
_exit:
	if (fnc1 != NULL)
		bit_stream_free(fnc1);
	bit_stream_free(src);
	bit_stream_free(dst);
	return NULL;
}

static void releaseQrCodeInfo(QrCodeInfo info) {
	free(info->data);
	free(info);
}

QrCodeInfo qr_decode(char *image_1bit, int width, int height, int sample_granularity) {
	QrCodeInfo info = (QrCodeInfo)calloc(1, sizeof(struct tagQrCodeInfo));
	info->release = releaseQrCodeInfo;
	float finder[3][4];
	float box[4][2];
	switch (scanFinder(image_1bit, width, height, sample_granularity < 1 ? 1 : sample_granularity, finder)) {
	case -1:
		info->reverse = !0;
		break;
	case 0:
		info->status = ERR_POSITIONING;
		return info;
	case 1:
		info->reverse = 0;
	}
	if (!scanBox(image_1bit, info->reverse, width, height, finder, box)){
		info->status = ERR_POSITIONING;
		return info;
	}
	struct view_port *vp = view_port_alloc(image_1bit, info->reverse, width, height, box, finder);
	if (vp == NULL) {
		info->status = ERR_POSITIONING;
		return info;
	}
	int version = info->version = scanVersion(vp, finder);
	if (version < 1){
		view_port_free(vp);
		info->status = ERR_VERSION_INFO;
		return info;
	}
	int size = 4 * info->version + 17;
	int area = size * size;
	char *modules = (char *)calloc(2, area);
	char *funmask = modules + area;
	initializeVersion(version, modules, funmask, size);
	if (version > 1) {
		int n = version / 7 + 2;
		int s = version == 32 ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
		int r[7];
		for (int i = n, p = version * 4 + 10; --i > 0; p -= s)
			r[i] = p;
		r[0] = 6;
		float alignments[7][7][2];
		if (!scanAlignments(vp, finder, r, n, alignments)){
			free(modules);
			view_port_free(vp);
			info->status = ERR_ALIGNMENTS;
			return info;
		}
		scanData(vp, finder, r, n, alignments, modules, funmask, size);
	}
	else {
		scanDataV1(vp, finder, modules, funmask, size);
	}
	int ecl, mask;
	void *codewords;
	int length;
	for (;; vp->mirror = 1){
		int format = scanFormat(vp, finder);
		if (format == -1) {
			if (vp->mirror){
				free(modules);
				view_port_free(vp);
				info->status = ERR_FORMAT_INFO;
				return info;
			}
		}
		else {
			ecl = format >> 3;
			mask = format & 7;
			maskPattern(modules, funmask, size, mask);
			if ((codewords = extractCodewords(modules, funmask, size, version, ecl, &length)) != NULL)
				break;
			if (vp->mirror) {
				free(modules);
				view_port_free(vp);
				info->status = UNSUPPORTED_ENCODE_MODE;
				return info;
			}
			maskPattern(modules, funmask, size, mask);
		}
		for (int i = 0; i < size - 1; i++)
			for (int j = i + 1; j < size; j++) {
				int a = i * size + j;
				int b = j * size + i;
				char t = modules[a];
				modules[a] = modules[b];
				modules[b] = t;
			}
	}
	info->ecl = ecl;
	info->mask = mask;
	info->mirror = vp->mirror;
	free(modules);
	view_port_free(vp);
	void *data = decodeCodewords(codewords, &length, info->version);
	if (data == NULL) {
		info->data = codewords;
		info->length = length;
		info->status = UNSUPPORTED_ENCODE_MODE;
		return info;
	}
	free(codewords);
	info->status = SCAN_OK;
	info->data = data;
	info->length = length;
	return info;
}
