package io.kite.intellij.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.parser.KiteParserDefinition;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a formatting block in the Kite language structure.
 * Handles indentation, spacing, and child block creation.
 */
public class KiteBlock extends AbstractBlock {
    private final SpacingBuilder spacingBuilder;
    private final Indent indent;
    private final Integer alignmentPadding; // For object property alignment

    public KiteBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull Indent indent,
                     @NotNull SpacingBuilder spacingBuilder) {
        this(node, wrap, alignment, indent, spacingBuilder, null);
    }

    public KiteBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull Indent indent,
                     @NotNull SpacingBuilder spacingBuilder,
                     @Nullable Integer alignmentPadding) {
        super(node, wrap, alignment);
        this.spacingBuilder = spacingBuilder;
        this.indent = indent;
        this.alignmentPadding = alignmentPadding;
    }

    @Override
    protected List<Block> buildChildren() {
        IElementType nodeType = myNode.getElementType();

        // Special handling for object literals - align colons (only for multi-line)
        if (nodeType == KiteElementTypes.OBJECT_LITERAL && isMultiLine(myNode)) {
            return buildAlignedChildren(KiteTokenTypes.COLON);
        }

        // Align equals in resource/component blocks
        if (nodeType == KiteElementTypes.RESOURCE_DECLARATION ||
            nodeType == KiteElementTypes.COMPONENT_DECLARATION) {
            return buildAlignedChildren(KiteTokenTypes.ASSIGN);
        }

        // Special handling for schema declarations (properties are nested)
        if (nodeType == KiteElementTypes.SCHEMA_DECLARATION) {
            return buildSchemaChildren(KiteTokenTypes.ASSIGN);
        }

        // FILE level: handle both decorator colons and declaration equals
        if (nodeType == KiteParserDefinition.FILE) {
            return buildFileChildren();
        }

        // Default block building for other elements
        List<Block> blocks = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();

        while (child != null) {
            IElementType childType = child.getElementType();

            // Skip only true whitespace (not newlines which formatter needs)
            if (childType != TokenType.WHITE_SPACE &&
                child.getTextLength() > 0) {

                Indent childIndent = getChildIndent(childType);

                blocks.add(new KiteBlock(
                    child,
                    null,
                    null,
                    childIndent,
                    spacingBuilder
                ));
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Checks if a node spans multiple lines.
     * Used to determine if alignment should be applied.
     */
    private boolean isMultiLine(ASTNode node) {
        String text = node.getText();
        return text.contains("\n");
    }

    /**
     * Checks if a node is inside parentheses (function call/decorator arguments).
     * Used to skip alignment for object literals used as function arguments.
     */
    private boolean isInsideParentheses(ASTNode node) {
        ASTNode parent = node.getTreeParent();
        if (parent == null) return false;

        // Check if any sibling before this node is LPAREN
        ASTNode sibling = parent.getFirstChildNode();
        boolean foundLParen = false;

        while (sibling != null && sibling != node) {
            if (sibling.getElementType() == KiteTokenTypes.LPAREN) {
                foundLParen = true;
                break;
            }
            sibling = sibling.getTreeNext();
        }

        return foundLParen;
    }

    /**
     * Checks if this node contains parenthesized named arguments (identifier : value).
     * This is used for decorator arguments like @validate(regex: "...", flag: 'i')
     */
    private boolean containsParenthesizedNamedArgs() {
        // Look for pattern: LPAREN ... IDENTIFIER ... COLON ... RPAREN
        boolean hasLParen = false;
        boolean hasIdentifier = false;
        boolean hasColon = false;

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType type = child.getElementType();

            if (type == KiteTokenTypes.LPAREN) {
                hasLParen = true;
            } else if (hasLParen && type == KiteTokenTypes.IDENTIFIER) {
                // Check if this identifier is followed by a colon
                if (findNextToken(child, KiteTokenTypes.COLON) != null) {
                    hasIdentifier = true;
                }
            } else if (hasLParen && hasIdentifier && type == KiteTokenTypes.COLON) {
                hasColon = true;
            } else if (type == KiteTokenTypes.RPAREN && hasColon) {
                return true; // Found the pattern
            }

            child = child.getTreeNext();
        }

        return false;
    }

    /**
     * Builds children with aligned tokens (colons or equals).
     * Used for object literals, parenthesized named arguments, and block declarations.
     * Creates blocks for all tokens, calculating padding for alignment.
     * Groups consecutive similar declarations and aligns within each group.
     *
     * @param alignToken The token type to align (COLON or ASSIGN)
     */
    private List<Block> buildAlignedChildren(IElementType alignToken) {
        List<Block> blocks = new ArrayList<>();

        // First pass: identify alignment groups and their max key lengths
        List<AlignmentGroup> groups = identifyAlignmentGroups(alignToken);

        // Second pass: build blocks with appropriate padding
        ASTNode child = myNode.getFirstChildNode();
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType != TokenType.WHITE_SPACE && child.getTextLength() > 0) {
                // For declaration elements, inline their children instead of creating a block for the element
                if (childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION ||
                    childType == KiteElementTypes.VARIABLE_DECLARATION) {

                    // Build blocks for the declaration's children
                    buildDeclarationBlocks(child, alignToken, groups, blocks);
                } else {
                    Indent childIndent = getChildIndent(childType);

                    // Track identifiers that precede the align token
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        ASTNode nextToken = findNextToken(child, alignToken);
                        if (nextToken != null) {
                            previousIdentifier = child;
                        }
                    }

                    // Calculate padding for the align token
                    Integer padding = null;
                    if (childType == alignToken && previousIdentifier != null) {
                        // Find which group this identifier belongs to
                        AlignmentGroup group = findGroupForIdentifier(groups, previousIdentifier);
                        if (group != null) {
                            int keyLength = previousIdentifier.getTextLength();
                            // For ASSIGN, always have at least 1 space; for COLON, longest key has 0 spaces
                            int extraSpace = (alignToken == KiteTokenTypes.ASSIGN) ? 1 : 0;
                            padding = group.maxKeyLength - keyLength + extraSpace;
                        }
                        previousIdentifier = null;
                    }

                    blocks.add(new KiteBlock(
                        child,
                        null,
                        null,
                        childIndent,
                        spacingBuilder,
                        padding
                    ));
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Builds children for schema declarations with proper alignment of ASSIGN tokens.
     * Schema properties are nested, so this method flattens all tokens and applies alignment.
     */
    private List<Block> buildSchemaChildren(IElementType alignToken) {
        List<Block> blocks = new ArrayList<>();

        // First pass: identify alignment groups
        List<AlignmentGroup> groups = identifySchemaPropertyGroups(alignToken);

        // Second pass: flatten all tokens and build blocks with alignment
        List<ASTNode> allTokens = new ArrayList<>();
        flattenTokens(myNode, allTokens);

        ASTNode prevIdentifier = null;
        boolean inSchemaBody = false;

        for (ASTNode token : allTokens) {
            IElementType tokenType = token.getElementType();

            // Skip whitespace
            if (tokenType == TokenType.WHITE_SPACE && token.getTextLength() > 0) {
                continue;
            }

            // Track when we're inside the schema body
            if (tokenType == KiteTokenTypes.LBRACE) {
                inSchemaBody = true;
            }

            // Handle identifiers - track pairs (type + property name)
            if (inSchemaBody && tokenType == KiteTokenTypes.IDENTIFIER) {
                if (prevIdentifier != null) {
                    // This is the property name (second identifier in a pair)
                    prevIdentifier = token;
                } else {
                    // This is the type (first identifier)
                    prevIdentifier = token;
                }
            }

            // Calculate padding for ASSIGN tokens
            Integer padding = null;
            if (inSchemaBody && tokenType == alignToken && prevIdentifier != null) {
                // Find which group this identifier belongs to
                AlignmentGroup group = findGroupForIdentifier(groups, prevIdentifier);
                if (group != null) {
                    int keyLength = prevIdentifier.getTextLength();
                    int extraSpace = 1; // For ASSIGN, always at least 1 space
                    padding = group.maxKeyLength - keyLength + extraSpace;
                }
                prevIdentifier = null;
            }

            // Reset prevIdentifier at end of property
            if (tokenType == KiteTokenTypes.NL || tokenType == KiteTokenTypes.NEWLINE ||
                tokenType == KiteTokenTypes.SEMICOLON) {
                prevIdentifier = null;
            }

            // Create block for this token
            if (token.getTextLength() > 0) {
                Indent tokenIndent = getChildIndent(tokenType);
                blocks.add(new KiteBlock(
                    token,
                    null,
                    null,
                    tokenIndent,
                    spacingBuilder,
                    padding
                ));
            }
        }

        return blocks;
    }

    /**
     * Recursively flattens all tokens in a tree into a list.
     */
    private void flattenTokens(ASTNode node, List<ASTNode> tokens) {
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getFirstChildNode() == null) {
                // Leaf node - add to list
                tokens.add(child);
            } else {
                // Non-leaf node - recurse
                flattenTokens(child, tokens);
            }
            child = child.getTreeNext();
        }
    }

    /**
     * Builds children for FILE node, handling both decorator colons and declaration equals.
     */
    private List<Block> buildFileChildren() {
        List<Block> blocks = new ArrayList<>();

        // Identify alignment groups for both COLON and ASSIGN
        List<AlignmentGroup> colonGroups = containsParenthesizedNamedArgs() ?
            identifyAlignmentGroups(KiteTokenTypes.COLON) : new ArrayList<>();
        List<AlignmentGroup> assignGroups = identifyAlignmentGroups(KiteTokenTypes.ASSIGN);

        ASTNode child = myNode.getFirstChildNode();
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType != TokenType.WHITE_SPACE && child.getTextLength() > 0) {
                Indent childIndent = getChildIndent(childType);

                // Track identifiers that precede align tokens
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    ASTNode nextColon = findNextToken(child, KiteTokenTypes.COLON);
                    ASTNode nextAssign = findNextToken(child, KiteTokenTypes.ASSIGN);
                    if (nextColon != null || nextAssign != null) {
                        previousIdentifier = child;
                    }
                }

                // Calculate padding for COLON or ASSIGN tokens
                Integer padding = null;
                if (previousIdentifier != null) {
                    if (childType == KiteTokenTypes.COLON) {
                        AlignmentGroup group = findGroupForIdentifier(colonGroups, previousIdentifier);
                        if (group != null) {
                            int keyLength = previousIdentifier.getTextLength();
                            padding = group.maxKeyLength - keyLength; // No extra space for colons
                        }
                        previousIdentifier = null;
                    } else if (childType == KiteTokenTypes.ASSIGN) {
                        AlignmentGroup group = findGroupForIdentifier(assignGroups, previousIdentifier);
                        if (group != null) {
                            int keyLength = previousIdentifier.getTextLength();
                            padding = group.maxKeyLength - keyLength + 1; // +1 for equals
                        }
                        previousIdentifier = null;
                    }
                }

                blocks.add(new KiteBlock(
                    child,
                    null,
                    null,
                    childIndent,
                    spacingBuilder,
                    padding
                ));
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Builds blocks for children of a declaration element (INPUT/OUTPUT/VAR).
     * Inlines the declaration's tokens rather than creating a block for the whole declaration.
     */
    private void buildDeclarationBlocks(ASTNode declarationElement,
                                       IElementType alignToken,
                                       List<AlignmentGroup> groups,
                                       List<Block> blocks) {
        ASTNode child = declarationElement.getFirstChildNode();
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType != TokenType.WHITE_SPACE && child.getTextLength() > 0) {
                Indent childIndent = getChildIndent(childType);

                // Track identifiers that precede the align token
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    ASTNode nextToken = findNextToken(child, alignToken);
                    if (nextToken != null) {
                        previousIdentifier = child;
                    }
                }

                // Calculate padding for the align token
                Integer padding = null;
                if (childType == alignToken && previousIdentifier != null) {
                    // Find which group this identifier belongs to
                    AlignmentGroup group = findGroupForIdentifier(groups, previousIdentifier);
                    if (group != null) {
                        int keyLength = previousIdentifier.getTextLength();
                        // For ASSIGN, always have at least 1 space; for COLON, longest key has 0 spaces
                        int extraSpace = (alignToken == KiteTokenTypes.ASSIGN) ? 1 : 0;
                        padding = group.maxKeyLength - keyLength + extraSpace;
                    }
                    previousIdentifier = null;
                }

                blocks.add(new KiteBlock(
                    child,
                    null,
                    null,
                    childIndent,
                    spacingBuilder,
                    padding
                ));
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Represents a group of consecutive declarations that should align together.
     */
    private static class AlignmentGroup {
        IElementType keywordType; // INPUT, OUTPUT, VAR, etc.
        int maxKeyLength;
        List<ASTNode> identifiers = new ArrayList<>();
    }

    /**
     * Identifies groups of consecutive similar declarations.
     * For object literals and decorator args: creates one group (align all together).
     * For block declarations: groups consecutive similar declarations, separated by blank lines.
     */
    private List<AlignmentGroup> identifyAlignmentGroups(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();

        // For object literals and decorator args, use simple grouping (all in one group)
        if (myNode.getElementType() == KiteElementTypes.OBJECT_LITERAL ||
            containsParenthesizedNamedArgs()) {
            return identifySimpleGroup(alignToken);
        }

        // For schema declarations, use special grouping to handle properties with and without default values
        if (myNode.getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            return identifySchemaPropertyGroups(alignToken);
        }

        // For block declarations, use smart grouping
        AlignmentGroup currentGroup = null;
        IElementType lastDeclType = null;
        boolean hasBlankLineSinceLastDecl = false;
        boolean lastWasRegularProperty = false;

        ASTNode child = myNode.getFirstChildNode();
        ASTNode prevChild = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            // Detect blank lines (two consecutive newlines)
            if (childType == KiteTokenTypes.NL || childType == KiteTokenTypes.NEWLINE) {
                if (prevChild != null &&
                    (prevChild.getElementType() == KiteTokenTypes.NL ||
                     prevChild.getElementType() == KiteTokenTypes.NEWLINE)) {
                    hasBlankLineSinceLastDecl = true;
                }
            }

            // Case 1: Declaration elements (INPUT_DECLARATION, etc.)
            if (childType == KiteElementTypes.INPUT_DECLARATION ||
                childType == KiteElementTypes.OUTPUT_DECLARATION ||
                childType == KiteElementTypes.VARIABLE_DECLARATION) {

                // Start new group if type changed or blank line
                if (currentGroup == null ||
                    childType != lastDeclType ||
                    hasBlankLineSinceLastDecl ||
                    lastWasRegularProperty) {
                    currentGroup = new AlignmentGroup();
                    currentGroup.keywordType = childType;
                    groups.add(currentGroup);
                    lastDeclType = childType;
                }

                hasBlankLineSinceLastDecl = false;
                lastWasRegularProperty = false;

                // Find the identifier before the align token inside this declaration
                ASTNode identifier = findIdentifierInDeclaration(child, alignToken);
                if (identifier != null) {
                    currentGroup.identifiers.add(identifier);
                    currentGroup.maxKeyLength = Math.max(currentGroup.maxKeyLength, identifier.getTextLength());
                }
            }
            // Case 2: Regular properties (raw IDENTIFIER followed by align token)
            else if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    // Start new group if switching from declarations or blank line
                    if (currentGroup == null ||
                        lastDeclType != null ||
                        hasBlankLineSinceLastDecl) {
                        currentGroup = new AlignmentGroup();
                        currentGroup.keywordType = null; // Regular properties have no keyword
                        groups.add(currentGroup);
                        lastDeclType = null;
                    }

                    hasBlankLineSinceLastDecl = false;
                    lastWasRegularProperty = true;

                    currentGroup.identifiers.add(child);
                    currentGroup.maxKeyLength = Math.max(currentGroup.maxKeyLength, child.getTextLength());
                }
            }

            prevChild = child;
            child = child.getTreeNext();
        }

        return groups;
    }

    /**
     * Identifies alignment groups for schema properties.
     * Schema properties have pattern: typeIdentifier identifier [= value]
     * We want to align ALL property names (second identifier), whether or not they have default values.
     * This method traverses all tokens recursively to handle nested structures.
     */
    private List<AlignmentGroup> identifySchemaPropertyGroups(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();
        AlignmentGroup currentGroup = new AlignmentGroup();
        groups.add(currentGroup);

        // Recursively collect all identifiers that should be aligned
        collectSchemaPropertyIdentifiers(myNode, currentGroup);

        return groups;
    }

    /**
     * Recursively collects schema property identifiers from a node and its descendants.
     */
    private void collectSchemaPropertyIdentifiers(ASTNode node, AlignmentGroup group) {
        ASTNode child = node.getFirstChildNode();
        ASTNode prevIdentifier = null;
        boolean inSchemaBody = false;

        while (child != null) {
            IElementType childType = child.getElementType();

            // Skip until we're inside the schema body (after LBRACE)
            if (childType == KiteTokenTypes.LBRACE) {
                inSchemaBody = true;
            } else if (childType == KiteTokenTypes.RBRACE) {
                break; // Stop at closing brace
            } else if (inSchemaBody && childType == KiteTokenTypes.IDENTIFIER) {
                if (prevIdentifier != null) {
                    // This is the second identifier in a pair - it's the property name
                    // Add it to the group whether or not it's followed by ASSIGN
                    group.identifiers.add(child);
                    group.maxKeyLength = Math.max(group.maxKeyLength, child.getTextLength());
                    prevIdentifier = null; // Reset for next pair
                } else {
                    // This is the first identifier - likely the type
                    prevIdentifier = child;
                }
            }
            // Reset prevIdentifier if we hit ASSIGN or other tokens that end a property
            else if (childType == KiteTokenTypes.ASSIGN ||
                     childType == KiteTokenTypes.NL ||
                     childType == KiteTokenTypes.NEWLINE ||
                     childType == KiteTokenTypes.SEMICOLON) {
                // Property ended, reset for next property
                prevIdentifier = null;
            }
            // For any non-leaf nodes, recurse into them
            else if (child.getFirstChildNode() != null) {
                collectSchemaPropertyIdentifiers(child, group);
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Checks if a token type represents a value (used in schema property parsing).
     */
    private boolean isValueToken(IElementType type) {
        return type == KiteTokenTypes.NUMBER ||
               type == KiteTokenTypes.STRING ||
               type == KiteTokenTypes.TRUE ||
               type == KiteTokenTypes.FALSE ||
               type == KiteTokenTypes.NULL ||
               type == KiteElementTypes.OBJECT_LITERAL ||
               type == KiteElementTypes.ARRAY_LITERAL;
    }

    /**
     * Creates alignment groups for identifiers, respecting parentheses boundaries.
     * For object literals: one group for all properties.
     * For decorator args (FILE level): separate group for each () pair.
     */
    private List<AlignmentGroup> identifySimpleGroup(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();
        AlignmentGroup group = null;
        int parenDepth = 0;
        boolean inParens = false;

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType childType = child.getElementType();

            // Track parentheses depth
            if (childType == KiteTokenTypes.LPAREN) {
                parenDepth++;
                if (parenDepth == 1) {
                    // Start new group for this parentheses scope
                    group = new AlignmentGroup();
                    groups.add(group);
                    inParens = true;
                }
            } else if (childType == KiteTokenTypes.RPAREN) {
                parenDepth--;
                if (parenDepth == 0) {
                    inParens = false;
                }
            }
            // Add identifiers to current group
            else if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    // For object literals (no parens tracking), create group if needed
                    if (!inParens && group == null) {
                        group = new AlignmentGroup();
                        groups.add(group);
                    }
                    // Only add if we have a group (either in parens or object literal)
                    if (group != null) {
                        group.identifiers.add(child);
                        group.maxKeyLength = Math.max(group.maxKeyLength, child.getTextLength());
                    }
                }
            }

            child = child.getTreeNext();
        }

        return groups;
    }

    /**
     * Finds the identifier before the align token inside a declaration element.
     * For INPUT/OUTPUT: skips the type, returns the property name
     * For VAR: returns the variable name
     */
    private ASTNode findIdentifierInDeclaration(ASTNode declarationElement, IElementType alignToken) {
        ASTNode child = declarationElement.getFirstChildNode();
        ASTNode lastIdentifier = null;

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at the align token
            if (type == alignToken) {
                return lastIdentifier;
            }

            // Track identifiers
            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            }

            child = child.getTreeNext();
        }

        return lastIdentifier;
    }

    /**
     * Finds the identifier before the align token, starting from a declaration keyword.
     */
    private ASTNode findIdentifierBeforeToken(ASTNode declarationKeyword, IElementType alignToken) {
        ASTNode node = declarationKeyword.getTreeNext();
        ASTNode lastIdentifier = null;

        while (node != null) {
            IElementType type = node.getElementType();

            // Stop at the align token
            if (type == alignToken) {
                return lastIdentifier;
            }

            // Track identifiers
            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = node;
            }

            // Stop at newline (end of declaration)
            if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                break;
            }

            node = node.getTreeNext();
        }

        return null;
    }

    /**
     * Finds which alignment group an identifier belongs to.
     */
    private AlignmentGroup findGroupForIdentifier(List<AlignmentGroup> groups, ASTNode identifier) {
        for (AlignmentGroup group : groups) {
            if (group.identifiers.contains(identifier)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Finds the length of the longest property key.
     *
     * @param alignToken The token type that follows keys (COLON or ASSIGN)
     */
    private int findMaxKeyLength(IElementType alignToken) {
        int maxLength = 0;
        ASTNode child = myNode.getFirstChildNode();

        while (child != null) {
            if (child.getElementType() == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    maxLength = Math.max(maxLength, child.getTextLength());
                }
            }
            child = child.getTreeNext();
        }

        return maxLength;
    }

    /**
     * Finds the next occurrence of a specific token after a node (skipping whitespace).
     *
     * @param node       The starting node
     * @param tokenType  The token type to find (COLON or ASSIGN)
     */
    private ASTNode findNextToken(ASTNode node, IElementType tokenType) {
        ASTNode next = node.getTreeNext();
        while (next != null) {
            IElementType type = next.getElementType();
            if (type == tokenType) {
                return next;
            }
            if (type != TokenType.WHITE_SPACE &&
                type != KiteTokenTypes.NL &&
                type != KiteTokenTypes.NEWLINE) {
                return null;
            }
            next = next.getTreeNext();
        }
        return null;
    }

    /**
     * Determines the indent for a child element based on its type.
     */
    private Indent getChildIndent(IElementType childType) {
        IElementType parentType = myNode.getElementType();

        // Content inside braces should be indented
        if (isBlockElement(parentType)) {
            // Braces get no indent (align with parent)
            if (childType == KiteTokenTypes.LBRACE || childType == KiteTokenTypes.RBRACE) {
                return Indent.getNoneIndent();
            }
            // Brackets and their content get normal indent
            return Indent.getNormalIndent();
        }

        // Content inside parentheses in function declarations and calls
        if (parentType == KiteTokenTypes.LPAREN || parentType == KiteTokenTypes.RPAREN) {
            return Indent.getContinuationWithoutFirstIndent();
        }

        return Indent.getNoneIndent();
    }

    /**
     * Checks if the element type is a block structure that should indent its children.
     */
    private boolean isBlockElement(IElementType type) {
        return type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT ||
               type == KiteElementTypes.WHILE_STATEMENT ||
               type == KiteElementTypes.OBJECT_LITERAL;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        // Use custom padding for aligned tokens (colons or equals)
        if (child2 instanceof KiteBlock) {
            KiteBlock kiteBlock2 = (KiteBlock) child2;
            IElementType tokenType = kiteBlock2.myNode.getElementType();

            if (kiteBlock2.alignmentPadding != null &&
                (tokenType == KiteTokenTypes.COLON || tokenType == KiteTokenTypes.ASSIGN)) {
                return Spacing.createSpacing(kiteBlock2.alignmentPadding, kiteBlock2.alignmentPadding, 0, true, 0);
            }
        }

        return spacingBuilder.getSpacing(this, child1, child2);
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }

    @Override
    public @NotNull Indent getIndent() {
        return indent;
    }

    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        IElementType type = myNode.getElementType();

        // Inside block structures, use normal indentation
        if (isBlockElement(type)) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }

        return new ChildAttributes(Indent.getNoneIndent(), null);
    }
}
