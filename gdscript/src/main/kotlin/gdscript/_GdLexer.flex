package gdscript;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static gdscript.psi.GdTypes.*;

%%

%{
  public _GdLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _GdLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

STRING=\"[^\"]*?\"
NUMBER=[0-9]+(\.)?[0-9]*
COMMENT=#.*
BAD_CHARACTER=[\^]

%%
<YYINITIAL> {
  {WHITE_SPACE}         { return WHITE_SPACE; }

  "\\"                  { return BACKSLASH; }
  "INDENT"              { return INDENT; }
  "NEW_LINE"            { return NEW_LINE; }
  "DEDENT"              { return DEDENT; }
  "EXTENDS"             { return EXTENDS; }
  "DOT"                 { return DOT; }
  "IDENTIFIER"          { return IDENTIFIER; }
  "CLASS_NAME"          { return CLASS_NAME; }
  "FUNC"                { return FUNC; }
  "CONST"               { return CONST; }
  "SIGNAL"              { return SIGNAL; }
  "VAR"                 { return VAR; }
  "ENUM"                { return ENUM; }
  "ANNOTATOR"           { return ANNOTATOR; }
  "REMOTE"              { return REMOTE; }
  "REMOTESYNC"          { return REMOTESYNC; }
  "MASTERSYNC"          { return MASTERSYNC; }
  "PUPPETSYNC"          { return PUPPETSYNC; }
  "MASTER"              { return MASTER; }
  "PUPPET"              { return PUPPET; }
  "STATIC"              { return STATIC; }
  "VARARG"              { return VARARG; }
  "RRBR"                { return RRBR; }
  "RCBR"                { return RCBR; }
  "RSBR"                { return RSBR; }
  "CLASS"               { return CLASS; }
  "LRBR"                { return LRBR; }
  "COMMA"               { return COMMA; }
  "COLON"               { return COLON; }
  "GET"                 { return GET; }
  "EQ"                  { return EQ; }
  "SET"                 { return SET; }
  "LCBR"                { return LCBR; }
  "PLUS"                { return PLUS; }
  "MINUS"               { return MINUS; }
  "RET"                 { return RET; }
  "VOID"                { return VOID; }
  "IF"                  { return IF; }
  "PASS"                { return PASS; }
  "CONTINUE"            { return CONTINUE; }
  "BREAK"               { return BREAK; }
  "BREAKPOINT"          { return BREAKPOINT; }
  "WHILE"               { return WHILE; }
  "FOR"                 { return FOR; }
  "MATCH"               { return MATCH; }
  "RETURN"              { return RETURN; }
  "AWAIT"               { return AWAIT; }
  "ASSET"               { return ASSET; }
  "NEGATE"              { return NEGATE; }
  "ELIF"                { return ELIF; }
  "ELSE"                { return ELSE; }
  "UNDER"               { return UNDER; }
  "IN"                  { return IN; }
  "LSBR"                { return LSBR; }
  "DOTDOT"              { return DOTDOT; }
  "ASSIGN"              { return ASSIGN; }
  "AS"                  { return AS; }
  "ANDAND"              { return ANDAND; }
  "OROR"                { return OROR; }
  "TEST_OPERATOR"       { return TEST_OPERATOR; }
  "AND"                 { return AND; }
  "XOR"                 { return XOR; }
  "OR"                  { return OR; }
  "LBSHIFT"             { return LBSHIFT; }
  "RBSHIFT"             { return RBSHIFT; }
  "MUL"                 { return MUL; }
  "DIV"                 { return DIV; }
  "MOD"                 { return MOD; }
  "POWER"               { return POWER; }
  "NOT"                 { return NOT; }
  "IS"                  { return IS; }
  "PPLUS"               { return PPLUS; }
  "MMINUS"              { return MMINUS; }
  "SELF"                { return SELF; }
  "SUPER"               { return SUPER; }
  "TRUE"                { return TRUE; }
  "FALSE"               { return FALSE; }
  "STRING_NAME"         { return STRING_NAME; }
  "NODE_PATH_LIT"       { return NODE_PATH_LIT; }
  "NULL"                { return NULL; }
  "NAN"                 { return NAN; }
  "INF"                 { return INF; }
  "SEMICON"             { return SEMICON; }
  "NODE_PATH_LEX"       { return NODE_PATH_LEX; }
  "CEQ"                 { return CEQ; }

  {STRING}              { return STRING; }
  {NUMBER}              { return NUMBER; }
  {COMMENT}             { return COMMENT; }
  {BAD_CHARACTER}       { return BAD_CHARACTER; }

}

[^] { return BAD_CHARACTER; }
