This is *experimental* code in support of the HTTP/2 development efforts. 

The code is provided as is with no warranty. It is provided as a supplement to various ongoing discussions on the IETF httpbis Working Groups efforts to define HTTP version 2.0.

The Apache v2.0 license applies to all source files.

---

# Delta Header Encoding (DHE)

## State Model:

The compressor and decompressor each maintain a cache of header value pairs.
There is a static cache, prepopulated by the specification, and a dynamic
cache, populated through the compression and decompression process. Each 
cache contains a maximum of 128 individual key value pairs. 

Each item in the index is referenced by an 8-bit identifier. The most 
significant bit identifies whether an item from the static or dynamic
cache is being referenced. Note: the Nil byte (0x00) is a valid identifier
for the dynamic cache.

`0xxxxxxx  --  Dynamic Cache
1xxxxxxx  --  Static Cache`

The dynamic cache is managed in a "least recently written" style, that is, as
the cache fills to capacity in both number of entries and maximum stored byte
size, the least recently written items are dropped and those indices are 
reused.

Indices from the dynamic cache are assigned in order, beginning from 0x00.

Each item in the store consists of a Header Name and a Value. The Name is a 
lower-case ISO-8859-1 character sequence. The Value is either a UTF-8 string, 
a number, a Timestamp or an arbitrary sequence of binary octets. 

The available size of the stored compression state can be capped by the 
decompressor. Each stored value contributes to the accumulated size of the
storage state. 

The size of string values is measured by the number of UTF-8 
bytes contained in the character sequence. (Note: NOT huffman-coded
bytes, raw UTF-8 bytes are counted).

The size of number and timestamp values are measured by the number of uvarint
encoded bytes it takes to represent the value (see the section of value 
types below).

The size of raw binary values is measured by the number of octets.

Header names DO NOT contribute to the stored state size of the compressor. 
ONLY VALUE SIZE is considered. Duplicate values MUST be counted individually.

## Header Groups:

Headers are serialized into four typed header groups, each represented by a
two-bit identifier.

`00 -- Index Header Group
01 -- Index Range Header Group
10 -- Cloned Index Header Group
11 -- Literal Header Group`

The Cloned Index (10) and Literal (11) header group types have an additional
"ephemeral" property that indicates whether or not the group affects the 
compression state. 

Each header group contains a single 8-bit prefix and up to 32 distinct header 
instances. 

### Header Group Prefix:

`00 0 00000`
  
The first two most significant bits of the header group prefix identify the 
group type. The next bit is the "ephemeral flag" and is used only for Cloned
and Literal group types. This bit indicates whether or not the group alters
the stored compression state.  The remaining five bits specify the number of 
header instances in the group, with 00000 indicating that the group contains 
1 instance and 11111 contains 32. A header group MUST contain at least one
instance.

The remaining serialization of the header group depends entirely on the 
group type. 

### Index Header Group:

The serialization of the Index Header Group consists of the Header Group Prefix
and up to 32 additional octets, each referencing a single 8-bit storage index
identifier for items in either the Static or Dynamic Cache.

For instance:
 
`00000000 00000000 = References item #0 from the dynamic cache
 
00000001 00000000 10000000 = References item #0 from the dynamic cache and 
                             item #0 from the static cache`
                              
Index Header Groups do not affect the stored compression state. If an Index
Header Group references a header index that has not yet been allocated, the 
deserialization MUST terminate with an error. This likely means that the 
compression state has become out of sync and needs to be reestablished.

### Index Range Header Group:

The serialization of the Index Range Header Group consists of the Header Group 
Prefix and up to 32 additional 2-octet (16 bits) pairs of 8-bit storage index
identifiers. Each pair specifies a sequential range of adjacent ranges. 

For instance,

`01000000 00000000 00000100 = References items #0-#4 from the dynamic cache.
                            (five distinct items total)`


 A range MAY span dynamic and static index values. Index values are treated
 as unsigned byte values, so indices from the static cache are numerically 
 greater than dynamic cache values.. e.g. 
 
`01000000 01111111 10000001 = References item #127 from the dynamic cache, and
                            items #0 and #1 from the static cache.`

Index Range Header Groups do not affect the stored compression state. If a 
range references a header index that has not yet been allocated, the
deserialization MUST terminate with an error. This likely means that the 
compression state has become out of sync and needs to be reestablished.

### Cloned Index Header Group:

The serialization of the Cloned Index Header Group consists of the Header Group
Prefix and up to 32 Index+Value pairs. Each Index+Value pair consists of a
leading 8-bit storage index of an existing stored header followed by a new
serialized value. The serialization of the value depends on the value type
(see discussion of Value serialization below).

The Cloned Header Group affects the stored compression state if, and only if,
the "ephemeral" flag in the Header Group Prefix is NOT set. If the header 
group is not marked as being ephemeral, then the specified value is stored in
the next available storage index using the key name from the referenced storage
index.

For instance, assume the dynamic cache currently contains an item at index #1 
with key name "foo" and value "bar", the following causes a new item to be 
added to the storage with key name "foo" and value "baz":

`10000000 00000001 00000000 00000100 
10111000 01001111 10110101 00100000`

(Explanation of the value syntax is given a bit later)

If a Cloned Header Group references a header index that has not yet been 
allocated, the deserialization MUST terminate with an error. This likely 
means that the compression state has become out of sync and needs to be
reestablished.

### Literal Header Group:

The serialization of the Literal Header Group consists of the Header Group
Prefix and up to 32 Name+Value pairs. Each Name+Value pair consists of a 
length-prefixed sequence of ISO-8859-1 bytes specifying the Header Name
followed by the serialized value. The serialization of the value depends 
on the value type. The length prefix is encoded as an unsigned variable
length integer (uvarint). The length prefix SHOULD NOT be longer than five
octets and SHOULD NOT specify a value larger than 0xFFFF. 

The Literal Heaer Group affects the stored compression state if, and only if,
the "ephemeral" flag in the Header Group Prefix is NOT set. If the header
group is not marked as being ephemeral, then the specified key name and value
is stored in the next available storage index.

For instance:

`11000000 00000011 01100110 01101111
01101111 00000000 00000010 10111000
01000100 11010010`

Stores a new header with name "foo" and value "baz" in the dynamic cache.


Each Header Group consists of up to 32 distinct Header Instances. If a 
particular serialization block contains more than 32 intances of a given 
type, then multiple instances of the Header Group Type can be included in 
the serialized block. For instance, if a given message contains 33 index 
references, the serialized block may contain two separate Index Header Groups.
While this is allowed, it is expected to be rare.

## Values:

Header Values can be one of four types, each identified by a two-bit identifier.

`
 00 -- UTF-8 Text
 01 -- Numeric
 10 -- Timetamp
 11 -- Raw Binary Octets
`
 
An individual value MAY consist of up to 32 distinct discreet "value instances". 
A value with multiple instances is considered, for all intensive purposes, to 
be a single value. 

Each serialized value is preceded by an 8-bit Value Prefix.

`00 0 00000`
 
The first two most significant bits specifies the value type, the third
significant bit is a reserved flag. Future iterations of this specification
might make use of this bit. The final five least-significant bits specify
the number of discreet instances in the value. 00000 indicates that one 
instance is included, 11111 indicates that 32 instances are included.
 
The remaining serialization depends entirely on the type.

### UTF-8 Text Values:

UTF-8 Text is encoded as a length-prefixed sequence of Huffman-encoded UTF-8
octets. The length prefix is encoded as an unsigned variable-length integer
specifying the number of octets representing the value after applying the 
Huffman-encoding.  

### Numeric Values:

Numeric values are encoded as unsigned variable-length integers (uvarint) of 
up to a maximum of 10-octets. Unsigned values larger than 64-bits (0xFFFFFFFF) 
MUST NOT be used. Negative values cannot be represented using this syntax.
The uvarint syntax is described below.

### Timestamp Values:

Timestamp values are encoded as unsigned variable-length integers specifying
the number of seconds that have passed since the standard Epoch. The syntax
is identical that used for Numeric Values. Dates prior to the epoch cannot
be represented using this syntax.

### Raw Binary Octets:

Binary values are encoded as a length prefixed sequence of arbitrary octets.
The length prefix is encoded as an unsigned variable length integer.

### Unsigned Variable Length Encoding:

`  def uvarint(num):
    return [] if num = 0
    ret = []
    while(num != 0):
      m = num >>> 7    ; unsigned shift left 7 bits
      ret.push (byte)((num & ~0x80) | ( m >  0 ? 0x80 : 0x00 ));
      num = m;
    return ret;`

The uvarint syntax is identical to that used by Google's protobufs. They 
are serialized with the least-significant bytes first in batches of 7-bits, 
with the most significant bit per byte reserved as a continuation bit. Values
less than or equal to 127 are serialized using at most one byte; values less 
than or equal to 16383 are serialized using at most two bytes; values less 
than or equal to 2097151 are serialized using at most three bytes. 

### Huffman Coding:

All UTF-8 text values are compressed using a modified static huffman code.
"Modified" because the encoded version may contain compact-representations 
of raw, arbitrary UTF-8 bytes that are not covered by the static huffman
code table.

There are two huffman tables in use, one for HTTP Requests and another for 
HTTP Responses, each covers UTF-8 codepoints strictly less than 128 as well
the fifty possible UTF-8 leading octets.

The encoded result MUST end with a specific terminal sequence of bits 
called the "HUFFMAN_EOF". Currently, the HUFFMAN_EOF is the same for both
the Request and Response tables, but that could change if the tables are 
regenerated. Currently, the HUFFMAN_EOF sequence is 101001.

Codepoints >= 128 are handled by first taking the leading octet of the UTF-8 
representation and serializing it's associated huffman code from the table
to the output stream, then, depending on the octets value, serializing the 
six least significant bits from each of the remaining trailing octets.

For instance, the UTF-8 character U+00D4 (LATIN CAPITAL LETTER O WITH 
CIRCUMFLEX), with UTF-8 representation of C394 (hex) is encoded as:

`  11000100 01010010 10010000`

The first 8-bits represents the huffman-table prefix, the first six most 
significant bytes of the second octet are taken directly from the six least
significant bits of the second UTF-8 byte (0x94). Following those six bits 
are the six bits of the HUFFMAN_EOF 10 1001, followed by four unset padding 
bits.

The number of raw UTF-8 bits to write depends on the value of the leading
octet. If the value is between 0xC2 and 0xDF (inclusive), six bits from the 
second continuation byte is encoded. If the value is between 0xE0 and 0xEF
(inclusive), six bits from the second and third continuation bytes are 
encoded. If the value is between 0xF0 and 0xF4 (inclusive), six bits from 
the second, third and fourth continuation bytes are encoded. UTF-8 codepoints
that require greater than four bytes to encode cannot be represented.

#### Request Code Table:

``
    (  0)  |11111111|11111111|11111111|0 [25]        1fffffe [25]
    (  1)  |11111111|11111111|11111111|1 [25]        1ffffff [25]
    (  2)  |11111111|11111111|11100000 [24]           ffffe0 [24]
    (  3)  |11111111|11111111|11100001 [24]           ffffe1 [24]
    (  4)  |11111111|11111111|11100010 [24]           ffffe2 [24]
    (  5)  |11111111|11111111|11100011 [24]           ffffe3 [24]
    (  6)  |11111111|11111111|11100100 [24]           ffffe4 [24]
    (  7)  |11111111|11111111|11100101 [24]           ffffe5 [24]
    (  8)  |11111111|11111111|11100110 [24]           ffffe6 [24]
    (  9)  |11111111|11111111|11100111 [24]           ffffe7 [24]
    ( 10)  |11111111|11111111|11101000 [24]           ffffe8 [24]
    ( 11)  |11111111|11111111|11101001 [24]           ffffe9 [24]
    ( 12)  |11111111|11111111|11101010 [24]           ffffea [24]
    ( 13)  |11111111|11111111|11101011 [24]           ffffeb [24]
    ( 14)  |11111111|11111111|11101100 [24]           ffffec [24]
    ( 15)  |11111111|11111111|11101101 [24]           ffffed [24]
    ( 16)  |11111111|11111111|11101110 [24]           ffffee [24]
    ( 17)  |11111111|11111111|11101111 [24]           ffffef [24]
    ( 18)  |11111111|11111111|11110000 [24]           fffff0 [24]
    ( 19)  |11111111|11111111|11110001 [24]           fffff1 [24]
    ( 20)  |11111111|11111111|11110010 [24]           fffff2 [24]
    ( 21)  |11111111|11111111|11110011 [24]           fffff3 [24]
    ( 22)  |11111111|11111111|11110100 [24]           fffff4 [24]
    ( 23)  |11111111|11111111|11110101 [24]           fffff5 [24]
    ( 24)  |11111111|11111111|11110110 [24]           fffff6 [24]
    ( 25)  |11111111|11111111|11110111 [24]           fffff7 [24]
    ( 26)  |11111111|11111111|11111000 [24]           fffff8 [24]
    ( 27)  |11111111|11111111|11111001 [24]           fffff9 [24]
    ( 28)  |11111111|11111111|11111010 [24]           fffffa [24]
    ( 29)  |11111111|11111111|11111011 [24]           fffffb [24]
    ( 30)  |11111111|11111111|11111100 [24]           fffffc [24]
    ( 31)  |11111111|11111111|11111101 [24]           fffffd [24]
' ' ( 32)  |11111111|0110 [12]                           ff6 [12]
'!' ( 33)  |11111111|0111 [12]                           ff7 [12]
'"' ( 34)  |11111111|111010 [14]                        3ffa [14]
'#' ( 35)  |11111111|1111100 [15]                       7ffc [15]
'$' ( 36)  |11111111|1111101 [15]                       7ffd [15]
'%' ( 37)  |011000 [6]                                    18 [6]
'&' ( 38)  |1010100 [7]                                   54 [7]
''' ( 39)  |11111111|1111110 [15]                       7ffe [15]
'(' ( 40)  |11111111|1000 [12]                           ff8 [12]
')' ( 41)  |11111111|1001 [12]                           ff9 [12]
'*' ( 42)  |11111111|1010 [12]                           ffa [12]
'+' ( 43)  |11111111|1011 [12]                           ffb [12]
',' ( 44)  |11111011|10 [10]                             3ee [10]
'-' ( 45)  |011001 [6]                                    19 [6]
'.' ( 46)  |00010 [5]                                      2 [5]
'/' ( 47)  |00011 [5]                                      3 [5]
'0' ( 48)  |011010 [6]                                    1a [6]
'1' ( 49)  |011011 [6]                                    1b [6]
'2' ( 50)  |011100 [6]                                    1c [6]
'3' ( 51)  |011101 [6]                                    1d [6]
'4' ( 52)  |1010101 [7]                                   55 [7]
'5' ( 53)  |1010110 [7]                                   56 [7]
'6' ( 54)  |1010111 [7]                                   57 [7]
'7' ( 55)  |1011000 [7]                                   58 [7]
'8' ( 56)  |1011001 [7]                                   59 [7]
'9' ( 57)  |1011010 [7]                                   5a [7]
':' ( 58)  |011110 [6]                                    1e [6]
';' ( 59)  |11111011|11 [10]                             3ef [10]
'<' ( 60)  |11111111|11111111|10 [18]                  3fffe [18]
'=' ( 61)  |011111 [6]                                    1f [6]
'>' ( 62)  |11111111|11111110|0 [17]                   1fffc [17]
'?' ( 63)  |11110110|0 [9]                               1ec [9]
'@' ( 64)  |11111111|11100 [13]                         1ffc [13]
'A' ( 65)  |10111010 [8]                                  ba [8]
'B' ( 66)  |11110110|1 [9]                               1ed [9]
'C' ( 67)  |10111011 [8]                                  bb [8]
'D' ( 68)  |10111100 [8]                                  bc [8]
'E' ( 69)  |11110111|0 [9]                               1ee [9]
'F' ( 70)  |10111101 [8]                                  bd [8]
'G' ( 71)  |11111100|00 [10]                             3f0 [10]
'H' ( 72)  |11111100|01 [10]                             3f1 [10]
'I' ( 73)  |11110111|1 [9]                               1ef [9]
'J' ( 74)  |11111100|10 [10]                             3f2 [10]
'K' ( 75)  |11111111|010 [11]                            7fa [11]
'L' ( 76)  |11111100|11 [10]                             3f3 [10]
'M' ( 77)  |11111000|0 [9]                               1f0 [9]
'N' ( 78)  |11111101|00 [10]                             3f4 [10]
'O' ( 79)  |11111101|01 [10]                             3f5 [10]
'P' ( 80)  |11111000|1 [9]                               1f1 [9]
'Q' ( 81)  |11111101|10 [10]                             3f6 [10]
'R' ( 82)  |11111001|0 [9]                               1f2 [9]
'S' ( 83)  |11111001|1 [9]                               1f3 [9]
'T' ( 84)  |11111010|0 [9]                               1f4 [9]
'U' ( 85)  |11111101|11 [10]                             3f7 [10]
'V' ( 86)  |11111110|00 [10]                             3f8 [10]
'W' ( 87)  |11111110|01 [10]                             3f9 [10]
'X' ( 88)  |11111110|10 [10]                             3fa [10]
'Y' ( 89)  |11111110|11 [10]                             3fb [10]
'Z' ( 90)  |11111111|00 [10]                             3fc [10]
'[' ( 91)  |11111111|111011 [14]                        3ffb [14]
'\' ( 92)  |11111111|11111111|11111110 [24]           fffffe [24]
']' ( 93)  |11111111|111100 [14]                        3ffc [14]
'^' ( 94)  |11111111|111101 [14]                        3ffd [14]
'_' ( 95)  |1011011 [7]                                   5b [7]
'`' ( 96)  |11111111|11111111|110 [19]                 7fffe [19]
'a' ( 97)  |00100 [5]                                      4 [5]
'b' ( 98)  |1011100 [7]                                   5c [7]
'c' ( 99)  |00101 [5]                                      5 [5]
'd' (100)  |100000 [6]                                    20 [6]
'e' (101)  |0000 [4]                                       0 [4]
'f' (102)  |100001 [6]                                    21 [6]
'g' (103)  |100010 [6]                                    22 [6]
'h' (104)  |100011 [6]                                    23 [6]
'i' (105)  |00110 [5]                                      6 [5]
'j' (106)  |10111110 [8]                                  be [8]
'k' (107)  |10111111 [8]                                  bf [8]
'l' (108)  |100100 [6]                                    24 [6]
'm' (109)  |100101 [6]                                    25 [6]
'n' (110)  |100110 [6]                                    26 [6]
'o' (111)  |00111 [5]                                      7 [5]
'p' (112)  |01000 [5]                                      8 [5]
'q' (113)  |11111010|1 [9]                               1f5 [9]
'r' (114)  |01001 [5]                                      9 [5]
's' (115)  |01010 [5]                                      a [5]
't' (116)  |01011 [5]                                      b [5]
'u' (117)  |100111 [6]                                    27 [6]
'v' (118)  |11000000 [8]                                  c0 [8]
'w' (119)  |101000 [6]                                    28 [6]
'x' (120)  |11000001 [8]                                  c1 [8]
'y' (121)  |11000010 [8]                                  c2 [8]
'z' (122)  |11111011|0 [9]                               1f6 [9]
'{' (123)  |11111111|11111110|1 [17]                   1fffd [17]
'|' (124)  |11111111|1100 [12]                           ffc [12]
'}' (125)  |11111111|11111111|0 [17]                   1fffe [17]
'~' (126)  |11111111|1101 [12]                           ffd [12]
    (127)  |101001 [6]                                    29 [6]
    (0xC2) |11000011 [8]                                  c3 [8]
    (0xC3) |11000100 [8]                                  c4 [8]
    (0xC4) |11000101 [8]                                  c5 [8]
    (0xC5) |11000110 [8]                                  c6 [8]
    (0xC6) |11000111 [8]                                  c7 [8]
    (0xC7) |11001000 [8]                                  c8 [8]
    (0xC8) |11001001 [8]                                  c9 [8]
    (0xC9) |11001010 [8]                                  ca [8]
    (0xCA) |11001011 [8]                                  cb [8]
    (0xCB) |11001100 [8]                                  cc [8]
    (0xCC) |11001101 [8]                                  cd [8]
    (0xCD) |11001110 [8]                                  ce [8]
    (0xCE) |11001111 [8]                                  cf [8]
    (0xCF) |11010000 [8]                                  d0 [8]
    (0xD0) |11010001 [8]                                  d1 [8]
    (0xD1) |11010010 [8]                                  d2 [8]
    (0xD2) |11010011 [8]                                  d3 [8]
    (0xD3) |11010100 [8]                                  d4 [8]
    (0xD4) |11010101 [8]                                  d5 [8]
    (0xD5) |11010110 [8]                                  d6 [8]
    (0xD6) |11010111 [8]                                  d7 [8]
    (0xD7) |11011000 [8]                                  d8 [8]
    (0xD8) |11011001 [8]                                  d9 [8]
    (0xD9) |11011010 [8]                                  da [8]
    (0xDA) |11011011 [8]                                  db [8]
    (0xDB) |11011100 [8]                                  dc [8]
    (0xDC) |11011101 [8]                                  dd [8]
    (0xDD) |11011110 [8]                                  de [8]
    (0xDE) |11011111 [8]                                  df [8]
    (0xDF) |11100000 [8]                                  e0 [8]
    (0xE0) |11100001 [8]                                  e1 [8]
    (0xE1) |11100010 [8]                                  e2 [8]
    (0xE2) |11100011 [8]                                  e3 [8]
    (0xE3) |11100100 [8]                                  e4 [8]
    (0xE4) |11100101 [8]                                  e5 [8]
    (0xE5) |11100110 [8]                                  e6 [8]
    (0xE6) |11100111 [8]                                  e7 [8]
    (0xE7) |11101000 [8]                                  e8 [8]
    (0xE8) |11101001 [8]                                  e9 [8]
    (0xE9) |11101010 [8]                                  ea [8]
    (0xEA) |11101011 [8]                                  eb [8]
    (0xEB) |11101100 [8]                                  ec [8]
    (0xEC) |11101101 [8]                                  ed [8]
    (0xED) |11101110 [8]                                  ee [8]
    (0xEE) |11101111 [8]                                  ef [8]
    (0xEF) |11110000 [8]                                  f0 [8]
    (0xF0) |11110001 [8]                                  f1 [8]
    (0xF1) |11110010 [8]                                  f2 [8]
    (0xF2) |11110011 [8]                                  f3 [8]
    (0xF3) |11110100 [8]                                  f4 [8]
    (0xF4) |11110101 [8]                                  f5 [8]``

#### Response Code Table:

`` 
    (  0)  |11111111|11111111|11111111|0 [25]        1fffffe [25]
    (  1)  |11111111|11111111|11111111|1 [25]        1ffffff [25]
    (  2)  |11111111|11111111|11100000 [24]           ffffe0 [24]
    (  3)  |11111111|11111111|11100001 [24]           ffffe1 [24]
    (  4)  |11111111|11111111|11100010 [24]           ffffe2 [24]
    (  5)  |11111111|11111111|11100011 [24]           ffffe3 [24]
    (  6)  |11111111|11111111|11100100 [24]           ffffe4 [24]
    (  7)  |11111111|11111111|11100101 [24]           ffffe5 [24]
    (  8)  |11111111|11111111|11100110 [24]           ffffe6 [24]
    (  9)  |11111111|11111111|11100111 [24]           ffffe7 [24]
    ( 10)  |11111111|11111111|11101000 [24]           ffffe8 [24]
    ( 11)  |11111111|11111111|11101001 [24]           ffffe9 [24]
    ( 12)  |11111111|11111111|11101010 [24]           ffffea [24]
    ( 13)  |11111111|11111111|11101011 [24]           ffffeb [24]
    ( 14)  |11111111|11111111|11101100 [24]           ffffec [24]
    ( 15)  |11111111|11111111|11101101 [24]           ffffed [24]
    ( 16)  |11111111|11111111|11101110 [24]           ffffee [24]
    ( 17)  |11111111|11111111|11101111 [24]           ffffef [24]
    ( 18)  |11111111|11111111|11110000 [24]           fffff0 [24]
    ( 19)  |11111111|11111111|11110001 [24]           fffff1 [24]
    ( 20)  |11111111|11111111|11110010 [24]           fffff2 [24]
    ( 21)  |11111111|11111111|11110011 [24]           fffff3 [24]
    ( 22)  |11111111|11111111|11110100 [24]           fffff4 [24]
    ( 23)  |11111111|11111111|11110101 [24]           fffff5 [24]
    ( 24)  |11111111|11111111|11110110 [24]           fffff6 [24]
    ( 25)  |11111111|11111111|11110111 [24]           fffff7 [24]
    ( 26)  |11111111|11111111|11111000 [24]           fffff8 [24]
    ( 27)  |11111111|11111111|11111001 [24]           fffff9 [24]
    ( 28)  |11111111|11111111|11111010 [24]           fffffa [24]
    ( 29)  |11111111|11111111|11111011 [24]           fffffb [24]
    ( 30)  |11111111|11111111|11111100 [24]           fffffc [24]
    ( 31)  |11111111|11111111|11111101 [24]           fffffd [24]
' ' ( 32)  |11111111|0110 [12]                           ff6 [12]
'!' ( 33)  |11111111|0111 [12]                           ff7 [12]
'"' ( 34)  |11111111|111010 [14]                        3ffa [14]
'#' ( 35)  |11111111|1111100 [15]                       7ffc [15]
'$' ( 36)  |11111111|1111101 [15]                       7ffd [15]
'%' ( 37)  |011000 [6]                                    18 [6]
'&' ( 38)  |1010100 [7]                                   54 [7]
''' ( 39)  |11111111|1111110 [15]                       7ffe [15]
'(' ( 40)  |11111111|1000 [12]                           ff8 [12]
')' ( 41)  |11111111|1001 [12]                           ff9 [12]
'*' ( 42)  |11111111|1010 [12]                           ffa [12]
'+' ( 43)  |11111111|1011 [12]                           ffb [12]
',' ( 44)  |11111011|10 [10]                             3ee [10]
'-' ( 45)  |011001 [6]                                    19 [6]
'.' ( 46)  |00010 [5]                                      2 [5]
'/' ( 47)  |00011 [5]                                      3 [5]
'0' ( 48)  |011010 [6]                                    1a [6]
'1' ( 49)  |011011 [6]                                    1b [6]
'2' ( 50)  |011100 [6]                                    1c [6]
'3' ( 51)  |011101 [6]                                    1d [6]
'4' ( 52)  |1010101 [7]                                   55 [7]
'5' ( 53)  |1010110 [7]                                   56 [7]
'6' ( 54)  |1010111 [7]                                   57 [7]
'7' ( 55)  |1011000 [7]                                   58 [7]
'8' ( 56)  |1011001 [7]                                   59 [7]
'9' ( 57)  |1011010 [7]                                   5a [7]
':' ( 58)  |011110 [6]                                    1e [6]
';' ( 59)  |11111011|11 [10]                             3ef [10]
'<' ( 60)  |11111111|11111111|10 [18]                  3fffe [18]
'=' ( 61)  |011111 [6]                                    1f [6]
'>' ( 62)  |11111111|11111110|0 [17]                   1fffc [17]
'?' ( 63)  |11110110|0 [9]                               1ec [9]
'@' ( 64)  |11111111|11100 [13]                         1ffc [13]
'A' ( 65)  |10111010 [8]                                  ba [8]
'B' ( 66)  |11110110|1 [9]                               1ed [9]
'C' ( 67)  |10111011 [8]                                  bb [8]
'D' ( 68)  |10111100 [8]                                  bc [8]
'E' ( 69)  |11110111|0 [9]                               1ee [9]
'F' ( 70)  |10111101 [8]                                  bd [8]
'G' ( 71)  |11111100|00 [10]                             3f0 [10]
'H' ( 72)  |11111100|01 [10]                             3f1 [10]
'I' ( 73)  |11110111|1 [9]                               1ef [9]
'J' ( 74)  |11111100|10 [10]                             3f2 [10]
'K' ( 75)  |11111111|010 [11]                            7fa [11]
'L' ( 76)  |11111100|11 [10]                             3f3 [10]
'M' ( 77)  |11111000|0 [9]                               1f0 [9]
'N' ( 78)  |11111101|00 [10]                             3f4 [10]
'O' ( 79)  |11111101|01 [10]                             3f5 [10]
'P' ( 80)  |11111000|1 [9]                               1f1 [9]
'Q' ( 81)  |11111101|10 [10]                             3f6 [10]
'R' ( 82)  |11111001|0 [9]                               1f2 [9]
'S' ( 83)  |11111001|1 [9]                               1f3 [9]
'T' ( 84)  |11111010|0 [9]                               1f4 [9]
'U' ( 85)  |11111101|11 [10]                             3f7 [10]
'V' ( 86)  |11111110|00 [10]                             3f8 [10]
'W' ( 87)  |11111110|01 [10]                             3f9 [10]
'X' ( 88)  |11111110|10 [10]                             3fa [10]
'Y' ( 89)  |11111110|11 [10]                             3fb [10]
'Z' ( 90)  |11111111|00 [10]                             3fc [10]
'[' ( 91)  |11111111|111011 [14]                        3ffb [14]
'\' ( 92)  |11111111|11111111|11111110 [24]           fffffe [24]
']' ( 93)  |11111111|111100 [14]                        3ffc [14]
'^' ( 94)  |11111111|111101 [14]                        3ffd [14]
'_' ( 95)  |1011011 [7]                                   5b [7]
'`' ( 96)  |11111111|11111111|110 [19]                 7fffe [19]
'a' ( 97)  |00100 [5]                                      4 [5]
'b' ( 98)  |1011100 [7]                                   5c [7]
'c' ( 99)  |00101 [5]                                      5 [5]
'd' (100)  |100000 [6]                                    20 [6]
'e' (101)  |0000 [4]                                       0 [4]
'f' (102)  |100001 [6]                                    21 [6]
'g' (103)  |100010 [6]                                    22 [6]
'h' (104)  |100011 [6]                                    23 [6]
'i' (105)  |00110 [5]                                      6 [5]
'j' (106)  |10111110 [8]                                  be [8]
'k' (107)  |10111111 [8]                                  bf [8]
'l' (108)  |100100 [6]                                    24 [6]
'm' (109)  |100101 [6]                                    25 [6]
'n' (110)  |100110 [6]                                    26 [6]
'o' (111)  |00111 [5]                                      7 [5]
'p' (112)  |01000 [5]                                      8 [5]
'q' (113)  |11111010|1 [9]                               1f5 [9]
'r' (114)  |01001 [5]                                      9 [5]
's' (115)  |01010 [5]                                      a [5]
't' (116)  |01011 [5]                                      b [5]
'u' (117)  |100111 [6]                                    27 [6]
'v' (118)  |11000000 [8]                                  c0 [8]
'w' (119)  |101000 [6]                                    28 [6]
'x' (120)  |11000001 [8]                                  c1 [8]
'y' (121)  |11000010 [8]                                  c2 [8]
'z' (122)  |11111011|0 [9]                               1f6 [9]
'{' (123)  |11111111|11111110|1 [17]                   1fffd [17]
'|' (124)  |11111111|1100 [12]                           ffc [12]
'}' (125)  |11111111|11111111|0 [17]                   1fffe [17]
'~' (126)  |11111111|1101 [12]                           ffd [12]
    (127)  |101001 [6]                                    29 [6]
    (0xC2) |11000011 [8]                                  c3 [8]
    (0xC3) |11000100 [8]                                  c4 [8]
    (0xC4) |11000101 [8]                                  c5 [8]
    (0xC5) |11000110 [8]                                  c6 [8]
    (0xC6) |11000111 [8]                                  c7 [8]
    (0xC7) |11001000 [8]                                  c8 [8]
    (0xC8) |11001001 [8]                                  c9 [8]
    (0xC9) |11001010 [8]                                  ca [8]
    (0xCA) |11001011 [8]                                  cb [8]
    (0xCB) |11001100 [8]                                  cc [8]
    (0xCC) |11001101 [8]                                  cd [8]
    (0xCD) |11001110 [8]                                  ce [8]
    (0xCE) |11001111 [8]                                  cf [8]
    (0xCF) |11010000 [8]                                  d0 [8]
    (0xD0) |11010001 [8]                                  d1 [8]
    (0xD1) |11010010 [8]                                  d2 [8]
    (0xD2) |11010011 [8]                                  d3 [8]
    (0xD3) |11010100 [8]                                  d4 [8]
    (0xD4) |11010101 [8]                                  d5 [8]
    (0xD5) |11010110 [8]                                  d6 [8]
    (0xD6) |11010111 [8]                                  d7 [8]
    (0xD7) |11011000 [8]                                  d8 [8]
    (0xD8) |11011001 [8]                                  d9 [8]
    (0xD9) |11011010 [8]                                  da [8]
    (0xDA) |11011011 [8]                                  db [8]
    (0xDB) |11011100 [8]                                  dc [8]
    (0xDC) |11011101 [8]                                  dd [8]
    (0xDD) |11011110 [8]                                  de [8]
    (0xDE) |11011111 [8]                                  df [8]
    (0xDF) |11100000 [8]                                  e0 [8]
    (0xE0) |11100001 [8]                                  e1 [8]
    (0xE1) |11100010 [8]                                  e2 [8]
    (0xE2) |11100011 [8]                                  e3 [8]
    (0xE3) |11100100 [8]                                  e4 [8]
    (0xE4) |11100101 [8]                                  e5 [8]
    (0xE5) |11100110 [8]                                  e6 [8]
    (0xE6) |11100111 [8]                                  e7 [8]
    (0xE7) |11101000 [8]                                  e8 [8]
    (0xE8) |11101001 [8]                                  e9 [8]
    (0xE9) |11101010 [8]                                  ea [8]
    (0xEA) |11101011 [8]                                  eb [8]
    (0xEB) |11101100 [8]                                  ec [8]
    (0xEC) |11101101 [8]                                  ed [8]
    (0xED) |11101110 [8]                                  ee [8]
    (0xEE) |11101111 [8]                                  ef [8]
    (0xEF) |11110000 [8]                                  f0 [8]
    (0xF0) |11110001 [8]                                  f1 [8]
    (0xF1) |11110010 [8]                                  f2 [8]
    (0xF2) |11110011 [8]                                  f3 [8]
    (0xF3) |11110100 [8]                                  f4 [8]
    (0xF4) |11110101 [8]                                  f5 [8]``

## Pre-populated Static Storage

`
    0x80 "date"                        = NIL
    0x81 ":scheme"                     = "https"
    0x82 ":scheme"                     = "http"
    0x83 ":scheme"                     = "ftp"
    0x84 ":method"                     = "get"
    0x85 ":method"                     = "post"
    0x86 ":method"                     = "put"
    0x87 ":method"                     = "delete"
    0x88 ":method"                     = "options"
    0x89 ":method"                     = "patch"
    0x8A ":method"                     = "connect"
    0x8B ":path"                       = "/"
    0x8C ":host"                       = NIL
    0x8D "cookie"                      = NIL
    0x8E ":status"                     = 100
    0x8F ":status"                     = 101
    0x90 ":status"                     = 102
    0x91 ":status"                     = 200
    0x92 ":status"                     = 201
    0x93 ":status"                     = 202
    0x94 ":status"                     = 203
    0x95 ":status"                     = 204
    0x96 ":status"                     = 205
    0x97 ":status"                     = 206
    0x98 ":status"                     = 207
    0x99 ":status"                     = 208
    0x9A ":status"                     = 300
    0x9B ":status"                     = 301
    0x9C ":status"                     = 302
    0x9D ":status"                     = 303
    0x9E ":status"                     = 304
    0x9F ":status"                     = 305
    0xA0 ":status"                     = 307
    0xA1 ":status"                     = 308
    0xA2 ":status"                     = 400
    0xA3 ":status"                     = 401
    0xA4 ":status"                     = 402
    0xA5 ":status"                     = 403
    0xA6 ":status"                     = 404
    0xA7 ":status"                     = 405
    0xA8 ":status"                     = 406
    0xA9 ":status"                     = 407
    0xAA ":status"                     = 408
    0xAB ":status"                     = 409
    0xAC ":status"                     = 410
    0xAD ":status"                     = 411
    0xAE ":status"                     = 412
    0xAF ":status"                     = 413
    0xB0 ":status"                     = 414
    0xB1 ":status"                     = 415
    0xB2 ":status"                     = 416
    0xB3 ":status"                     = 417
    0xB4 ":status"                     = 500
    0xB5 ":status"                     = 501
    0xB6 ":status"                     = 502
    0xB7 ":status"                     = 503
    0xB8 ":status"                     = 504
    0xB9 ":status"                     = 505
    0xBA ":status-text"                = "OK"
    0xBB ":version"                    = "1.1"
    0xBC "accept"                      = NIL
    0xBD "accept-charset"              = NIL
    0xBE "accept-encoding"             = NIL
    0xBF "accept-language"             = NIL
    0xC0 "accept-ranges"               = NIL
    0xC1 "allow"                       = NIL
    0xC2 "authorization"               = NIL
    0xC3 "cache-control"               = NIL
    0xC4 "content-base"                = NIL
    0xC5 "content-encoding"            = NIL
    0xC6 "content-length"              = NIL
    0xC7 "content-location"            = NIL
    0xC8 "content-md5"                 = NIL
    0xC9 "content-range"               = NIL
    0xCA "content-type"                = NIL
    0xCB "content-disposition"         = NIL
    0xCC "content-language"            = NIL
    0xCD "etag"                        = NIL
    0xCE "expect"                      = NIL
    0xCF "expires"                     = NIL
    0xD0 "from"                        = NIL
    0xD1 "if-match"                    = NIL
    0xD2 "if-modified-since"           = NIL
    0xD3 "if-none-match"               = NIL
    0xD4 "if-range"                    = NIL
    0xD5 "if-unmodified-since"         = NIL
    0xD6 "last-modified"               = NIL
    0xD7 "location"                    = NIL
    0xD8 "max-forwards"                = NIL
    0xD9 "origin"                      = NIL
    0xDA "pragma"                      = NIL
    0xDB "proxy-authenticate"          = NIL
    0xDC "proxy-authorization"         = NIL
    0xDD "range"                       = NIL
    0xDE "referer"                     = NIL
    0xDF "retry-after"                 = NIL
    0xE0 "server"                      = NIL
    0xE1 "set-cookie"                  = NIL
    0xE2 "status"                      = NIL
    0xE3 "te"                          = NIL
    0xE4 "trailer"                     = NIL
    0xE5 "transfer-encoding"           = NIL
    0xE6 "upgrade"                     = NIL
    0xE7 "user-agent"                  = NIL
    0xE8 "vary"                        = NIL
    0xE9 "via"                         = NIL
    0xEA "warning"                     = NIL
    0xEB "www-authenticate"            = NIL
    0xEC "access-control-allow-origin" = NIL
    0xED "get-dictionary"              = NIL
    0xEE "p3p"                         = NIL
    0xEF "link"                        = NIL
    0xF0 "prefer"                      = NIL
    0xF1 "preference-applied"          = NIL
    0xF2 "accept-patch"                = NIL
    0xF3 NIL
    0xF4 NIL
    0xF5 NIL
    0xF6 NIL
    0xF7 NIL
    0xF8 NIL
    0xF9 NIL
    0xFA NIL
    0xFB NIL
    0xFC NIL
    0xFD NIL
    0xFE NIL
    0xFF NIL
`